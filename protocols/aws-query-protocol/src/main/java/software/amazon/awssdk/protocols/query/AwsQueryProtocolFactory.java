/*
 * Copyright 2010-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.protocols.query;

import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.awssdk.annotations.SdkProtectedApi;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.Request;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.http.HttpResponseHandler;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.core.ProtocolMarshaller;
import software.amazon.awssdk.protocols.query.internal.marshall.QueryProtocolMarshaller;
import software.amazon.awssdk.protocols.query.internal.unmarshall.AwsQueryResponseHandler;
import software.amazon.awssdk.protocols.query.internal.unmarshall.QueryProtocolUnmarshaller;
import software.amazon.awssdk.protocols.query.unmarshall.AwsQueryErrorProtocolUnmarshaller;
import software.amazon.awssdk.protocols.query.unmarshall.XmlElement;

/**
 * Protocol factory for the AWS/Query protocol.
 */
@SdkProtectedApi
public class AwsQueryProtocolFactory {

    private final Map<String, Supplier<SdkPojo>> modeledExceptions;
    private final Supplier<SdkPojo> defaultServiceExceptionSupplier;
    private final AwsQueryErrorProtocolUnmarshaller errorUnmarshaller;

    AwsQueryProtocolFactory(Builder<?> builder) {
        this.modeledExceptions = unmodifiableMap(new HashMap<>(builder.modeledExceptions));
        this.defaultServiceExceptionSupplier = builder.defaultServiceExceptionSupplier;
        this.errorUnmarshaller = AwsQueryErrorProtocolUnmarshaller
            .builder()
            .defaultExceptionSupplier(defaultServiceExceptionSupplier)
            .exceptions(modeledExceptions)
            // We don't set result wrapper since that's handled by the errorRootExtractor
            .errorUnmarshaller(QueryProtocolUnmarshaller.builder().build())
            .errorRootExtractor(this::getErrorRoot)
            .build();
    }

    /**
     * Creates a new marshaller for the given request.
     *
     * @param operationInfo Object containing metadata about the operation.
     * @param origRequest Request to marshall.
     * @param <T> Type to marshall.
     * @return New {@link ProtocolMarshaller}.
     */
    public <T extends software.amazon.awssdk.awscore.AwsRequest> ProtocolMarshaller<Request<T>> createProtocolMarshaller(
        OperationInfo operationInfo, T origRequest) {
        return QueryProtocolMarshaller.builder(origRequest)
                                      .operationInfo(operationInfo)
                                      .isEc2(isEc2())
                                      .build();
    }

    /**
     * Creates the success response handler to unmarshall the response into a POJO.
     *
     * @param pojoSupplier Supplier of the POJO builder we are unmarshalling into.
     * @param <T> Type being unmarshalled.
     * @return New {@link HttpResponseHandler} for success responses.
     */
    public <T extends AwsResponse> HttpResponseHandler<T> createResponseHandler(Supplier<SdkPojo> pojoSupplier) {
        return new AwsQueryResponseHandler<>(QueryProtocolUnmarshaller.builder()
                                                                      .hasResultWrapper(!isEc2())
                                                                      .build(),
            r -> pojoSupplier.get());
    }

    /**
     * @return A {@link HttpResponseHandler} that will unmarshall the service exceptional response into
     * a modeled exception or the service base exception.
     */
    public HttpResponseHandler<AwsServiceException> createErrorResponseHandler() {
        return errorUnmarshaller;
    }

    /**
     * Extracts the <Error/> element from the root XML document. Method is protected as EC2 has a slightly
     * different location.
     *
     * @param document Root XML document.
     * @return If error root is found than a fulfilled {@link Optional}, otherwise an empty one.
     */
    Optional<XmlElement> getErrorRoot(XmlElement document) {
        return document.getOptionalElementByName("Error");
    }

    /**
     * EC2 has a few distinct differences from query so we wire things up a bit differently.
     */
    boolean isEc2() {
        return false;
    }

    /**
     * @return New Builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AwsQueryProtocolFactory}.
     *
     * @param <SubclassT> Subclass of Builder for fluent method chaining.
     */
    public static class Builder<SubclassT extends Builder> {

        private final Map<String, Supplier<SdkPojo>> modeledExceptions = new HashMap<>();
        private Supplier<SdkPojo> defaultServiceExceptionSupplier;

        Builder() {
        }

        /**
         * Registers a new modeled exception by the error code.
         *
         * @param errorCode Error code identifying this modeled exception.
         * @param exceptionBuilderSupplier Supplier of the modeled exceptions Builder.
         * @return This builder for method chaining.
         */
        public SubclassT registerModeledException(String errorCode, Supplier<SdkPojo> exceptionBuilderSupplier) {
            modeledExceptions.put(errorCode, exceptionBuilderSupplier);
            return getSubclass();
        }

        /**
         * A supplier for the services base exception builder. This is used when we can't identify any modeled
         * exception to unmarshall into.
         *
         * @param exceptionBuilderSupplier Suppplier of the base service exceptions Builder.
         * @return This builder for method chaining.
         */
        public SubclassT defaultServiceExceptionSupplier(Supplier<SdkPojo> exceptionBuilderSupplier) {
            this.defaultServiceExceptionSupplier = exceptionBuilderSupplier;
            return getSubclass();
        }

        @SuppressWarnings("unchecked")
        private SubclassT getSubclass() {
            return (SubclassT) this;
        }

        /**
         * @return New instance of {@link AwsQueryProtocolFactory}.
         */
        public AwsQueryProtocolFactory build() {
            return new AwsQueryProtocolFactory(this);
        }
    }
}
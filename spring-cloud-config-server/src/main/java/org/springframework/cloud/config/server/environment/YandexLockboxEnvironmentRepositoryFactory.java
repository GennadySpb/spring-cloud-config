/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.environment;

import java.time.Duration;

import yandex.cloud.api.compute.v1.ImageServiceGrpc;
import yandex.cloud.api.compute.v1.InstanceServiceGrpc;
import yandex.cloud.api.lockbox.v1.PayloadServiceGrpc;
import yandex.cloud.api.lockbox.v1.SecretServiceGrpc;
import yandex.cloud.api.operation.OperationServiceGrpc;
import yandex.cloud.sdk.ServiceFactory;
import yandex.cloud.sdk.auth.Auth;

import org.springframework.cloud.config.server.config.ConfigServerProperties;

public class YandexLockboxEnvironmentRepositoryFactory implements
		EnvironmentRepositoryFactory<YandexLockboxEnvironmentRepository, YandexLockboxEnvironmentProperties> {

	private final ConfigServerProperties configServerProperties;

	public YandexLockboxEnvironmentRepositoryFactory(ConfigServerProperties configServerProperties) {
		this.configServerProperties = configServerProperties;
	}

	@Override
	public YandexLockboxEnvironmentRepository build(YandexLockboxEnvironmentProperties environmentProperties) {

		// Configuration
		ServiceFactory factory = ServiceFactory.builder()
				.credentialProvider(Auth.oauthTokenBuilder().fromEnv("YC_OAUTH")).requestTimeout(Duration.ofMinutes(1))
				.build();
		InstanceServiceGrpc.InstanceServiceBlockingStub instanceService = factory
				.create(InstanceServiceGrpc.InstanceServiceBlockingStub.class, InstanceServiceGrpc::newBlockingStub);
		OperationServiceGrpc.OperationServiceBlockingStub operationService = factory
				.create(OperationServiceGrpc.OperationServiceBlockingStub.class, OperationServiceGrpc::newBlockingStub);
		ImageServiceGrpc.ImageServiceBlockingStub imageService = factory
				.create(ImageServiceGrpc.ImageServiceBlockingStub.class, ImageServiceGrpc::newBlockingStub);
		PayloadServiceGrpc.PayloadServiceBlockingStub payloadService = factory
				.create(PayloadServiceGrpc.PayloadServiceBlockingStub.class, PayloadServiceGrpc::newBlockingStub);
		SecretServiceGrpc.SecretServiceBlockingStub secretServiceBlockingStub = factory
				.create(SecretServiceGrpc.SecretServiceBlockingStub.class, SecretServiceGrpc::newBlockingStub);
		// PayloadServiceGrpc.PayloadServiceBlockingStub payloadService =
		// (PayloadServiceGrpc.PayloadServiceBlockingStub) secretServiceBlockingStub;
		// Lockbox.ImageServiceBlockingStub payloabService = payloadService;

		// YandexLockboxClientBuilder clientBuilder = YandexLockboxClientBuilder
		// .standard();

		// YandexLockbox client = clientBuilder.build();
		return new YandexLockboxEnvironmentRepository(payloadService, configServerProperties, environmentProperties);
	}

}

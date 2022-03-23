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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.secretmanager.v1.Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import yandex.cloud.api.lockbox.v1.PayloadOuterClass;
import yandex.cloud.api.lockbox.v1.PayloadServiceGrpc;
import yandex.cloud.api.lockbox.v1.PayloadServiceOuterClass;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.secretmanager.GoogleSecretComparatorByVersion;
import org.springframework.cloud.config.server.environment.secretmanager.HttpHeaderGoogleConfigProvider;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentProperties.DEFAULT_PATH_SEPARATOR;

public class YandexLockboxEnvironmentRepository implements EnvironmentRepository {

	private String applicationLabel;

	private String profileLabel;

	private boolean tokenMandatory;


	private static final Log log = LogFactory
		.getLog(org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentRepository.class);

	private final ObjectMapper objectMapper;

	private final PayloadServiceGrpc.PayloadServiceBlockingStub psClient;

	private final ConfigServerProperties configServerProperties;

	private final YandexLockboxEnvironmentProperties environmentProperties;

	public YandexLockboxEnvironmentRepository(PayloadServiceGrpc.PayloadServiceBlockingStub payloadService,
		ConfigServerProperties configServerProperties, YandexLockboxEnvironmentProperties properties) {
		this.applicationLabel = properties.getApplicationLabel();
		this.profileLabel = properties.getProfileLabel();
		this.tokenMandatory = properties.getTokenMandatory();


		this.psClient = payloadService;
		this.configServerProperties = configServerProperties;
		this.environmentProperties = properties;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
//		final String defaultApplication = configServerProperties.getDefaultApplicationName();
//		final String defaultProfile = configServerProperties.getDefaultProfile();

		if (StringUtils.isEmpty(label)) {
			label = "master";
		}
		if (StringUtils.isEmpty(profile)) {
			profile = "default";
		}
		if (!profile.startsWith("default")) {
			profile = "default," + profile;
		}

		String[] profiles = org.springframework.util.StringUtils
			.trimArrayElements(org.springframework.util.StringUtils.commaDelimitedListToStringArray(profile));

		Environment result = new Environment(application, profile, label, null, null);
		if (tokenMandatory) {
			if (accessStrategy.checkRemotePermissions()) {
				addPropertySource(application, profiles, result);
			}
		}
		else {
			addPropertySource(application, profiles, result);
		}
		return result;
	}

	private void addPropertySource(String application, String[] profiles, Environment result) {
		for (String profileUnit : profiles) {
			Map<?, ?> secrets = getSecrets(application, profileUnit);
			if (!secrets.isEmpty()) {
				result.add(new PropertySource("gsm:" + application + "-" + profileUnit, secrets));
			}
		}
	}

	private Map<?, ?> getSecrets(String application, String profile) {
		Map<String, String> result = new HashMap<>();
		String prefix = configProvider.getValue(HttpHeaderGoogleConfigProvider.PREFIX_HEADER, false);
		for (Secret secret : accessStrategy.getSecrets()) {
			if (secret.getLabelsOrDefault(applicationLabel, "application").equalsIgnoreCase(application)
				&& secret.getLabelsOrDefault(profileLabel, "profile").equalsIgnoreCase(profile)) {
				result.put(accessStrategy.getSecretName(secret),
					accessStrategy.getSecretValue(secret, new GoogleSecretComparatorByVersion()));
			}
			else if (org.apache.commons.lang3.StringUtils.isNotBlank(prefix) && accessStrategy.getSecretName(secret).startsWith(prefix)) {
				result.put(org.apache.commons.lang3.StringUtils.removeStart(accessStrategy.getSecretName(secret), prefix),
					accessStrategy.getSecretValue(secret, new GoogleSecretComparatorByVersion()));
			}
		}
		return result;
	}

	private String buildPath(String application, String profile) {
		String prefix = environmentProperties.getPrefix();
		String profileSeparator = environmentProperties.getProfileSeparator();

		if (profile == null || profile.isEmpty()) {
			return prefix + DEFAULT_PATH_SEPARATOR + application + DEFAULT_PATH_SEPARATOR;
		}
		else {
			return prefix + DEFAULT_PATH_SEPARATOR + application + profileSeparator + profile + DEFAULT_PATH_SEPARATOR;
		}
	}

	private Map<Object, Object> findProperties(String path) {
		Map<Object, Object> properties = new HashMap<>();

//		private PP.Payload getPayload(@NotNull String secretId, @NotNull String serviceAccountId) {

//		var withCallCredentialsPayloadService = this.payloadStub.withCallCredentials(
//			new IamCallCredentials(serviceAccountService.getToken(serviceAccountId)));
//		return safeGet(() -> withCallCredentialsPayloadService.get(payloadRequest));

		GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(path);
		try {
			var payloadRequest = PayloadServiceOuterClass.GetPayloadRequest.newBuilder().setSecretId(path).build();
			PayloadOuterClass.Payload response = psClient.get(payloadRequest);


			if (response != null) {
				Map<String, Object> secretMap = objectMapper.readValue(response.getEntries(0).toString()),
					new TypeReference<Map<String, Object>>() {
					});

				for (Map.Entry<String, Object> secretEntry : secretMap.entrySet()) {
					properties.put(secretEntry.getKey(), secretEntry.getValue());
				}
			}
		}
		catch (ResourceNotFoundException | IOException e) {
			log.debug(String.format(
				"Skip adding propertySource. Unable to load secrets from AWS Secrets Manager for secretId=%s",
				path), e);
		}

		return properties;
	}

}

// @Override
// public Environment findOne(String application, String profile, String label) {
// return null;
// }
// }

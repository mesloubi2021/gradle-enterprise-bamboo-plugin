package com.gradle.enterprise.bamboo;

import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.credentials.CredentialsAccessor;
import com.atlassian.bamboo.credentials.CredentialsData;
import com.atlassian.bamboo.task.runtime.RuntimeTaskDefinition;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.variable.VariableContext;
import com.atlassian.bandana.BandanaContext;
import com.atlassian.bandana.BandanaManager;
import com.gradle.enterprise.bamboo.config.PersistentConfiguration;
import com.gradle.enterprise.bamboo.config.PersistentConfigurationManager;
import com.gradle.enterprise.bamboo.config.UsernameAndPassword;
import com.gradle.enterprise.bamboo.config.UsernameAndPasswordCredentialsProvider;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GradleEnterprisePreJobActionTest {

    private final BandanaManager bandanaManager = mock(BandanaManager.class);
    private final CredentialsAccessor credentialsAccessor = mock(CredentialsAccessor.class);

    private final StageExecution stageExecution = mock(StageExecution.class);
    private final BuildContext buildContext = mock(BuildContext.class);
    private final VariableContext variableContext = mock(VariableContext.class);

    private final GradleBuildScanInjector gradleBuildScanInjector =
        new GradleBuildScanInjector(null, null, null, null);

    private final GradleEnterprisePreJobAction gradleEnterprisePreJobAction =
        new GradleEnterprisePreJobAction(
            new PersistentConfigurationManager(bandanaManager),
            new UsernameAndPasswordCredentialsProvider(credentialsAccessor),
            Collections.singletonList(gradleBuildScanInjector)
        );

    @Test
    void doesNothingIfNoConfiguration() {
        // when
        gradleEnterprisePreJobAction.execute(stageExecution, buildContext);

        // then
        verify(bandanaManager, times(1)).getValue(any(BandanaContext.class), anyString());
        verifyNoInteractions(credentialsAccessor);
    }

    @Test
    void doesNothingIfNoSharedCredentials() {
        // given
        when(bandanaManager.getValue(any(BandanaContext.class), anyString()))
            .thenReturn(new PersistentConfiguration());

        // when
        gradleEnterprisePreJobAction.execute(stageExecution, buildContext);

        // then
        verify(bandanaManager, times(1)).getValue(any(BandanaContext.class), anyString());
        verifyNoInteractions(credentialsAccessor);
    }

    @Test
    void doesNothingIfCredentialsNotFound() {
        // given
        String credentialsName = RandomStringUtils.randomAlphanumeric(10);
        when(bandanaManager.getValue(any(BandanaContext.class), anyString()))
            .thenReturn(new PersistentConfiguration().setSharedCredentialName(credentialsName));

        // when
        gradleEnterprisePreJobAction.execute(stageExecution, buildContext);

        // then
        verify(credentialsAccessor, times(1)).getCredentialsByName(credentialsName);
        verifyNoInteractions(buildContext);
    }

    @Test
    void doesNothingIfCredentialsWithoutPassword() {
        // given
        CredentialsData credentialsData = mock(CredentialsData.class);
        when(credentialsData.getPluginKey()).thenReturn(UsernameAndPassword.SHARED_USERNAME_PASSWORD_PLUGIN_KEY);
        when(credentialsData.getConfiguration()).thenReturn(Collections.emptyMap());

        String credentialsName = RandomStringUtils.randomAlphanumeric(10);
        when(bandanaManager.getValue(any(BandanaContext.class), anyString()))
            .thenReturn(new PersistentConfiguration().setSharedCredentialName(credentialsName));
        when(credentialsAccessor.getCredentialsByName(credentialsName))
            .thenReturn(credentialsData);

        // when
        gradleEnterprisePreJobAction.execute(stageExecution, buildContext);

        // then
        verify(credentialsAccessor, times(1)).getCredentialsByName(credentialsName);
        verifyNoInteractions(buildContext);
    }

    @Test
    void doesNothingIfNoSupportedTasks() {
        // given
        String accessKey = String.format("scans.gradle.com=%s", RandomStringUtils.randomAlphanumeric(10));
        CredentialsData credentialsData = mock(CredentialsData.class);
        when(credentialsData.getPluginKey()).thenReturn(UsernameAndPassword.SHARED_USERNAME_PASSWORD_PLUGIN_KEY);
        when(credentialsData.getConfiguration()).thenReturn(Collections.singletonMap(UsernameAndPassword.PASSWORD, accessKey));

        String credentialsName = RandomStringUtils.randomAlphanumeric(10);
        when(bandanaManager.getValue(any(BandanaContext.class), anyString()))
            .thenReturn(
                new PersistentConfiguration()
                    .setServer("https://scans.gradle.com")
                    .setSharedCredentialName(credentialsName)
                    .setGePluginVersion("3.12"));
        when(credentialsAccessor.getCredentialsByName(credentialsName))
            .thenReturn(credentialsData);

        RuntimeTaskDefinition runtimeTaskDefinition = mock(RuntimeTaskDefinition.class);
        when(runtimeTaskDefinition.isEnabled()).thenReturn(true);
        when(runtimeTaskDefinition.getPluginKey()).thenReturn("unsupported_plugin_key");
        when(buildContext.getRuntimeTaskDefinitions()).thenReturn(Collections.singletonList(runtimeTaskDefinition));

        // when
        gradleEnterprisePreJobAction.execute(stageExecution, buildContext);

        // then
        assertThat(gradleBuildScanInjector.hasSupportedTasks(buildContext), is(false));
        verify(buildContext, never()).getVariableContext();
    }

    @Test
    void doesNothingIfInjectionDisabled() {
        // given
        String accessKey = String.format("scans.gradle.com=%s", RandomStringUtils.randomAlphanumeric(10));
        CredentialsData credentialsData = mock(CredentialsData.class);
        when(credentialsData.getPluginKey()).thenReturn(UsernameAndPassword.SHARED_USERNAME_PASSWORD_PLUGIN_KEY);
        when(credentialsData.getConfiguration()).thenReturn(Collections.singletonMap(UsernameAndPassword.PASSWORD, accessKey));

        String credentialsName = RandomStringUtils.randomAlphanumeric(10);
        when(bandanaManager.getValue(any(BandanaContext.class), anyString()))
            .thenReturn(
                new PersistentConfiguration()
                    .setServer("https://scans.gradle.com")
                    .setSharedCredentialName(credentialsName));
        when(credentialsAccessor.getCredentialsByName(credentialsName))
            .thenReturn(credentialsData);

        RuntimeTaskDefinition runtimeTaskDefinition = mock(RuntimeTaskDefinition.class);
        when(runtimeTaskDefinition.isEnabled()).thenReturn(true);
        when(runtimeTaskDefinition.getPluginKey()).thenReturn(GradleBuildScanInjector.SCRIPT_PLUGIN_KEY);
        when(buildContext.getRuntimeTaskDefinitions()).thenReturn(Collections.singletonList(runtimeTaskDefinition));

        // when
        gradleEnterprisePreJobAction.execute(stageExecution, buildContext);

        // then
        assertThat(gradleBuildScanInjector.hasSupportedTasks(buildContext), is(true));
        verify(buildContext, never()).getVariableContext();
    }

    @Test
    void addsAccessKeyToContext() {
        // given
        String accessKey = String.format("scans.gradle.com=%s", RandomStringUtils.randomAlphanumeric(10));
        CredentialsData credentialsData = mock(CredentialsData.class);
        when(credentialsData.getPluginKey()).thenReturn(UsernameAndPassword.SHARED_USERNAME_PASSWORD_PLUGIN_KEY);
        when(credentialsData.getConfiguration()).thenReturn(Collections.singletonMap(UsernameAndPassword.PASSWORD, accessKey));

        String credentialsName = RandomStringUtils.randomAlphanumeric(10);
        when(bandanaManager.getValue(any(BandanaContext.class), anyString()))
            .thenReturn(
                new PersistentConfiguration()
                    .setServer("https://scans.gradle.com")
                    .setSharedCredentialName(credentialsName)
                    .setGePluginVersion("3.12"));
        when(credentialsAccessor.getCredentialsByName(credentialsName))
            .thenReturn(credentialsData);

        RuntimeTaskDefinition runtimeTaskDefinition = mock(RuntimeTaskDefinition.class);
        when(runtimeTaskDefinition.isEnabled()).thenReturn(true);
        when(runtimeTaskDefinition.getPluginKey()).thenReturn(GradleBuildScanInjector.SCRIPT_PLUGIN_KEY);
        when(buildContext.getRuntimeTaskDefinitions()).thenReturn(Collections.singletonList(runtimeTaskDefinition));
        when(buildContext.getVariableContext()).thenReturn(variableContext);

        // when
        gradleEnterprisePreJobAction.execute(stageExecution, buildContext);

        // then
        verify(variableContext, times(1)).addLocalVariable(Constants.ACCESS_KEY, accessKey);
    }
}

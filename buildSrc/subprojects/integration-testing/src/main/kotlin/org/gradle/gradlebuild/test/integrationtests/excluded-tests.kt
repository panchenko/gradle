package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.JavaVersion.VERSION_1_10
import org.gradle.api.JavaVersion.VERSION_1_9


internal
val excludedTests = listOf(
    // TODO requires investigation
    "DaemonGroovyCompilerIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "DaemonJavaCompilerIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "InProcessJavaCompilerIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "JacocoPluginMultiVersionIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),
    "JacocoPluginCoverageVerificationIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),

    // Caused by: java.lang.IncompatibleClassChangeError: Method Person.getName()Ljava/lang/String; must be InterfaceMethodref constant
    // Fail since build 125
    "InterfaceBackedManagedTypeIntegrationTest" to listOf(VERSION_1_9, VERSION_1_10),

    // Cannot obtain Jvm arguments via java.lang.management.ManagementFactory.runtimeMXBean.inputArguments module java.management does not export sun.management to unnamed module @6427ecb
    "BuildEnvironmentModelCrossVersionSpec" to listOf(VERSION_1_9, VERSION_1_10), // "informs about java args as in the build script"
    "JavaConfigurabilityCrossVersionSpec" to listOf(VERSION_1_9, VERSION_1_10), // "customized java args are reflected in the inputArguments and the build model", "tooling api provided jvm args take precedence over gradle.properties"
    "GradlePropertiesToolingApiCrossVersionSpec" to listOf(VERSION_1_9, VERSION_1_10), // "tooling api honours jvm args specified in gradle.properties"

    // Test compiles for Java 5
    "ToolingApiUnsupportedClientJvmCrossVersionSpec" to listOf(VERSION_1_9, VERSION_1_10)
)

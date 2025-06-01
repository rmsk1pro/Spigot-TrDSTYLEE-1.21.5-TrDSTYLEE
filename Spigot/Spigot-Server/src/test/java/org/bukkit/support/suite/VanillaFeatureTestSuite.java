package org.bukkit.support.suite;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Test suite for test which need vanilla registry values present")
@IncludeTags("VanillaFeature")
@SelectPackages("org.bukkit")
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class VanillaFeatureTestSuite {
}

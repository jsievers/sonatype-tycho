package org.sonatype.tycho.test.tycho134;

import java.io.File;
import java.net.URL;

import junit.framework.Assert;

import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.sonatype.tycho.test.AbstractTychoIntegrationTest;
import org.sonatype.tycho.test.util.UpdateSiteUtil;

/* java -jar \eclipse\plugins\org.eclipse.equinox.launcher_1.0.1.R33x_v20080118.jar -application org.eclipse.update.core.siteOptimizer -digestBuilder -digestOutputDir=d:\temp\eclipse\digest -siteXML=D:\sonatype\workspace\tycho\tycho-its\projects\tycho129\tycho.demo.site\target\site\site.xml  -jarProcessor -processAll -pack -outputDir d:\temp\eclipse\site D:\sonatype\workspace\tycho\tycho-its\projects\tycho129\tycho.demo.site\target\site */
public class Tycho134SignTest extends AbstractTychoIntegrationTest {

	@Test
	@SuppressWarnings("unchecked")
	public void signSite() throws Exception {
		Verifier verifier = getVerifier("/tycho134");
		verifier.getCliOptions().add(
				"-Dkeystore="
						+ getResourceFile("tycho134/.keystore")
								.getAbsolutePath());
		verifier.getCliOptions().add("-Dstorepass=sonatype-keystore");
		verifier.getCliOptions().add("-Dalias=tycho");
		verifier.getCliOptions().add("-Dkeypass=tycho-signing");

		verifier.executeGoal("package");
		verifier.verifyErrorFreeLog();

		File site = new File(verifier.getBasedir(),
				"tycho.demo.site/target/site");

		checkUpdatesite(verifier, site);

		checkPack200Files(site);

	}

	private void checkUpdatesite(Verifier verifier, File site) throws Exception {
		File mirror = new File(verifier.getBasedir(), "target/site");
		UpdateSiteUtil.mirrorSite(site, mirror);

		File siteXml = new File(mirror, "site.xml");
		Assert.assertTrue("Site.xml should be downloaded at mirror " + mirror,
				siteXml.exists());
		File feature = new File(mirror, "features/tycho.demo.feature_1.0.0.jar");
		Assert.assertTrue("Feature should be downloaded at mirror " + mirror,
				feature.exists());

		File plugin = new File(mirror, "plugins/tycho.demo_1.0.0.jar");
		Assert.assertTrue("Plugin should be downloaded at mirror " + mirror,
				plugin.exists());

	}

	private void checkPack200Files(File site) {
		// pack 200 are not downloaded by mirror
		File feature = new File(site,
				"features/tycho.demo.feature_1.0.0.jar.pack.gz");
		Assert.assertTrue("Feature pack should exist " + feature, feature
				.exists());

		File plugin = new File(site, "plugins/tycho.demo_1.0.0.jar.pack.gz");
		Assert
				.assertTrue("Plugin pack should exist " + plugin, plugin
						.exists());
	}

	private File getResourceFile(String relativePath) {
		URL root = AbstractTychoIntegrationTest.class.getResource("/");
		return new File(root.getFile(), relativePath);
	}

}

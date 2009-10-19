package org.codehaus.tycho.osgitools.targetplatform;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;

import copy.org.eclipse.core.runtime.internal.adaptor.PluginConverterImpl;

/**
 * Finds bundles in Eclipse installation. 
 * 
 * See http://wiki.eclipse.org/Equinox_p2_Getting_Started
 * See http://mea-bloga.blogspot.com/2008/04/new-target-platform-preference.html
 * 
 * @author igor
 *
 */

@Component(role=EclipseInstallationLayout.class, instantiationStrategy="per-lookup")
public class EclipseInstallationLayout extends AbstractLogEnabled {

	public static final String PLUGINS = "plugins";
	public static final String FEATURES = "features";

	private File location;
	private File dropinsLocation;

	public void setLocation(File location) {
		this.location = location;
		this.dropinsLocation = new File(location, "dropins");
	}

	public Set<File> getFeatures(File site) {
		Set<File> result = new LinkedHashSet<File>();

		File[] plugins = new File(site, FEATURES).listFiles();
		if (plugins != null) {
			result.addAll(Arrays.asList(plugins));
		}

		return result;
	}

	public Set<File> getInstalledPlugins() {
		Set<File> result = new LinkedHashSet<File>();
		try {
			result.addAll(readBundlesTxt(location));
		} catch (IOException e) {
			getLogger().warn("Exception reading P2 bundles list", e);
		}
		return result;
	}

	public Set<File> getPlugins(File site) {
		Set<File> result = new LinkedHashSet<File>();
		
		addPlugins(result, new File(site, PLUGINS).listFiles());
		
		// check for bundles in the root of dropins directory
		if (dropinsLocation.equals(site)) {
			addPlugins(result, site.listFiles());
		}

		return result;
	}

	private void addPlugins(Set<File> result, File[] plugins) {
		if (plugins != null) {
			for (File plugin : plugins) {
				if (plugin.isDirectory() && isDIrectoryPlugin(plugin)) {
					result.add(plugin);
				} else if (plugin.isFile() && plugin.getName().endsWith(".jar")) {
					result.add(plugin);
				}
			}
		}
	}

	private boolean isDIrectoryPlugin(File plugin) {
		return new File(plugin, "META-INF/MANIFEST.MF").canRead()
			|| new File(plugin, PluginConverterImpl.PLUGIN_MANIFEST).canRead()
			|| new File(plugin, PluginConverterImpl.FRAGMENT_MANIFEST).canRead();
	}

	public Set<File> getSites() {
		Set<File> result = new LinkedHashSet<File>();

		if (location == null) {
		    return result;
		}

		if (new File(location, PLUGINS).isDirectory()) {
			result.add(location);
		}

		File platform = new File(location, "configuration/org.eclipse.update/platform.xml");
		if (platform.canRead()) {
			try {
				FileInputStream is = new FileInputStream(platform);
				try {
					XmlStreamReader reader = new XmlStreamReader(is);
					Xpp3Dom dom = Xpp3DomBuilder.build(reader);
					Xpp3Dom[] sites = dom.getChildren("site");
					for (Xpp3Dom site : sites) {
						String enabled = site.getAttribute("enabled");
						if (enabled == null || Boolean.parseBoolean(enabled)) {
							File dir = parsePlatformURL(location, site.getAttribute("url"));
							if (dir != null) {
								result.add(dir);
							}
						}
					}
				} finally {
					is.close();
				}
			} catch (Exception e) {
				getLogger().warn("Exception parsing " + toString(platform), e);
			}
		}

		addLinks(result, location, new File(location, "links"));

		// deal with dropins folder
		result.add(dropinsLocation);

		File[] dropinsFiles = dropinsLocation.listFiles();
		if (dropinsFiles != null) {
			for (File dropinsFile : dropinsFiles) {
				File plugins = new File(dropinsFile, PLUGINS);
				if (plugins.isDirectory()) {
					result.add(plugins.getParentFile());
					continue;
				}
				plugins = new File(dropinsFile, "eclipse/plugins");
				if (plugins.isDirectory()) {
					result.add(plugins.getParentFile());
				}
			}
		}

		addLinks(result, location, dropinsLocation);

		return result;
	}

	private String toString(File file) {
		try {
			return file.getCanonicalPath();
		} catch (IOException e) {
			return file.getAbsolutePath();
		}
	}

	private void addLinks(Set<File> result, File targetPlatform, File linksFolder) {
		File[] links = linksFolder.listFiles();
		if (links != null) { 
			for (File link : links) {
				if (link.isFile() && link.canRead() && link.getName().endsWith(".link")) {
					Properties props = new Properties();
					try {
						InputStream is = new FileInputStream(link);
						try {
							props.load(is);
						} finally {
							is.close();
						}
						String path = props.getProperty("path");
						if (path != null) {
							File dir = new File(path);
							if (!dir.isAbsolute() && targetPlatform.getParentFile() != null) {
								dir = new File(targetPlatform.getParentFile(), path);
							}
							dir = dir.getCanonicalFile();
							if (dir.isDirectory()) {
								result.add(dir);
							}
						}
					} catch (Exception e) {
						getLogger().warn("Exception parsing " + toString(link), e);
						continue;
					}
				}
			}
		}
	}

	private static final String PLATFORM_BASE_PREFIX = "platform:/base/";
	private static final String FILE_PREFIX = "file:";
	
	private File parsePlatformURL(File platformBase, String url) {
		if (url == null) {
			return null;
		}

		url = url.replace('\\', '/');

		String relPath = null;
		if (url.startsWith(PLATFORM_BASE_PREFIX)) {
			relPath = url.substring(PLATFORM_BASE_PREFIX.length());
		} else if (url.startsWith(FILE_PREFIX)) {
			relPath = url.substring(FILE_PREFIX.length());
		}

		if (relPath == null) {
			return null;
		}

		if (relPath.length() > 0 && relPath.charAt(0) == '/') {
			return new File(relPath);
		}

		return new File(platformBase, relPath);
	}

	private List<File> readBundlesTxt(File platformBase) throws IOException {
		getLogger().debug("Reading P2 bundles list");
		
		// there is no way to find location of bundle pool without access to P2 profile
		// so lets assume equinox.launcher comes from the pool
		File eclipseIni = new File(platformBase, "eclipse.ini");
		File pool = platformBase;
		if (eclipseIni.isFile() && eclipseIni.canRead()) {
			BufferedReader in = new BufferedReader(new FileReader(eclipseIni));
			try {
				String str = null;
				while ((str = in.readLine()) != null) {
					if ("-startup".equals(str.trim())) {
						str = in.readLine();
						if (str != null) {
							File file = new File(str);
							if (!file.isAbsolute()) {
								file = new File(platformBase, str).getCanonicalFile();
							}
							pool = file.getParentFile().getParentFile().getCanonicalFile();
						}
						break;
					}
				}
			} finally {
				in.close();
			}
		}

		getLogger().debug("Bundle pool location " + toString(pool));

		File bundlesInfo = new File(platformBase, "configuration/org.eclipse.equinox.simpleconfigurator/bundles.info");
		if (!bundlesInfo.isFile() || !bundlesInfo.canRead()) {
			getLogger().info("Could not read P2 bundles list " + toString(bundlesInfo));
			return null;
		}

		ArrayList<File> plugins = new ArrayList<File>();

		BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(bundlesInfo)));
		String line;
		try {
			while ((line = r.readLine()) != null) {
				if (line.startsWith("#")) //$NON-NLS-1$
					continue;
				line = line.trim();
				if (line.length() == 0)
					continue;

				// (expectedState is an integer).
				if (line.startsWith("org.eclipse.equinox.simpleconfigurator.baseUrl" + "=")) { //$NON-NLS-1$ //$NON-NLS-2$
					continue;
				}

				StringTokenizer tok = new StringTokenizer(line, ",");
				/*String symbolicName =*/ tok.nextToken();
				/*String version =*/ tok.nextToken();
				String location = tok.nextToken();
				
				plugins.add(parsePlatformURL(pool, location));
			}
		} finally {
			r.close();
		}
		
		return plugins;
	}

}

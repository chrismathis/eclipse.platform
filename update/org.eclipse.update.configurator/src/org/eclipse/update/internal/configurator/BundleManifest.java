/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.internal.configurator;
import java.io.*;
import java.util.jar.*;

import org.eclipse.osgi.util.*;
import org.osgi.framework.*;
/**
 * Parses MANIFEST.MF
 */
public class BundleManifest implements IConfigurationConstants {
	private File manifestFile;
	private PluginEntry pluginEntry;
	private IOException exception;
	private String bundleURL;
	/**
	 * Constructor for local file
	 */
	public BundleManifest(File manifest) {
		super();
		manifestFile = manifest;
		if (manifest.exists() && !manifest.isDirectory()) {
			FileInputStream fos = null;
			try {
				fos = new FileInputStream(manifest);
				parse(fos);
			} catch (IOException ioe) {
			} finally {
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}
		/**
		 * Constructor for local file
		 */
		public BundleManifest(InputStream input, String bundleUrl) {
			super();
			bundleURL = bundleUrl;
			if (input != null) {
				parse(input);
			}
		}
	/**
	 * Parses manifest, creates PluginEntry if manifest is valid, stores
	 * exception if any occurs
	 * 
	 * @param in
	 *            InputStream
	 */
	private void parse(InputStream in) {
		try {
			Manifest m = new Manifest(in);
			Attributes a = m.getMainAttributes();
			// plugin id
			String symbolicName = a.getValue(Constants.BUNDLE_SYMBOLICNAME);
			if (symbolicName == null) {
				// In Eclipse manifest must have Bundle-SymbolicName attribute
				return;
			}
			String id;
			try {
				ManifestElement[] elements = ManifestElement.parseHeader(
						Constants.BUNDLE_SYMBOLICNAME, symbolicName);
				id = elements[0].getValue();
			} catch (BundleException be) {
				throw new IOException(be.getMessage());
			}
			// plugin version
			String version = a.getValue(Constants.BUNDLE_VERSION);
			String hostPlugin = a.getValue(Constants.FRAGMENT_HOST);
			pluginEntry = new PluginEntry();
			pluginEntry.setVersionedIdentifier(new VersionedIdentifier(id,
					version));
			pluginEntry.isFragment(hostPlugin != null
					&& hostPlugin.length() > 0);
			// Set URL
			if(bundleURL!=null){
				pluginEntry.setURL(bundleURL);
			}else{
				File pluginDir = manifestFile.getParentFile();
				if (pluginDir != null) {
					pluginDir = pluginDir.getParentFile();
				}
				if (pluginDir != null){
					pluginEntry.setURL(PLUGINS + "/" + pluginDir.getName() + "/"); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			//
		} catch (IOException ioe) {
			exception = ioe;
		}
	}
	public boolean exists() {
		return exception != null || pluginEntry != null;
	}
	/**
	 * Obtains PluginEntry from a manifest.
	 * 
	 * @return PluginEntry or null if valid manifest does not exist
	 * @throws IOException
	 *             if exception during parsing
	 */
	public PluginEntry getPluginEntry() throws IOException {
		if (exception != null) {
			throw exception;
		} else {
			return pluginEntry;
		}
	}
}
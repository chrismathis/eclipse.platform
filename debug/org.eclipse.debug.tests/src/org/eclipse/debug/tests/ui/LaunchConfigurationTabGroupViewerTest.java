/*******************************************************************************
 * Copyright (c) 2018, 2019 SAP SE and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     SAP SE - initial version
 *******************************************************************************/

package org.eclipse.debug.tests.ui;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationPresentationManager;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationsDialog;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.debug.ui.ILaunchConfigurationTabGroup;
import org.eclipse.swt.widgets.Display;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class LaunchConfigurationTabGroupViewerTest {

	private static interface ThrowingRunnable<T extends Exception> {
		void run() throws T;
	}

	private static final String LAUNCH_CONFIG_TYPE_ID = "org.eclipse.debug.ui.tests.launchConfigurationType1";
	private static final String LAUNCH_CONFIG_MODE = ILaunchManager.RUN_MODE;
	private ILaunchConfigurationType fLaunchConfigurationType;
	private LaunchConfigurationsDialog fLaunchConfigurationsDialog;

	@Before
	public void createDialog() throws CoreException {
		fLaunchConfigurationType = getLaunchManager().getLaunchConfigurationType(LAUNCH_CONFIG_TYPE_ID);
		ILaunchConfigurationTabGroup tabGroup = getLaunchConfigurationTabGroup(fLaunchConfigurationType);

		fLaunchConfigurationsDialog = (LaunchConfigurationsDialog) createLaunchConfigurationDialog();
		tabGroup.createTabs(fLaunchConfigurationsDialog, ILaunchManager.RUN_MODE);

		ILaunchConfigurationTab[] tabs = tabGroup.getTabs();

		assertThat(tabs).hasSizeGreaterThanOrEqualTo(2);
		assertThat(tabs).allSatisfy(tab -> assertThat(tab).isInstanceOf(SpyTab.class));
		int typesOfTabs = Math.toIntExact(Arrays.stream(tabs).map(Object::getClass).distinct().count());
		assertThat(tabs).withFailMessage("there are tabs of the exact same type in the group").hasSize(typesOfTabs);
	}

	@Test
	public void testAllTabsAreInitializedByDefault() {
		// Create a launch configuration with a unique name
		ThrowingRunnable<CoreException> createAndSelect1LaunchConfig = () -> {
			fLaunchConfigurationsDialog.getTabViewer().setInput(createLaunchConfigurationInstance());
		};

		final ILaunchConfigurationTab[] tabs = runOnDialog(createAndSelect1LaunchConfig);

		for (int i = 0; i < tabs.length; i++) {
			assertThat(((SpyTab) tabs[i])).withFailMessage("tab %s was not initialized", i).matches(SpyTab::isInitialized);
		}
	}

	@Test
	public void testFirstTabIsActivatedByDefault() {
		// Create a launch configuration with a unique name
		ThrowingRunnable<CoreException> createAndSelect1LaunchConfig = () -> {
			fLaunchConfigurationsDialog.getTabViewer().setInput(createLaunchConfigurationInstance());
		};

		final ILaunchConfigurationTab[] tabs = runOnDialog(createAndSelect1LaunchConfig);
		assertThat(((SpyTab) tabs[0])).matches(SpyTab::isActivated, "is activated");
	}

	@Test
	public void testOtherTabInOtherConfigIsActivated() {
		int secondTabIndex = 1;

		ThrowingRunnable<CoreException> setActiveTab = () -> {
			// Create and select launch config
			fLaunchConfigurationsDialog.getTabViewer().setInput(createLaunchConfigurationInstance());

			// Select another tab
			fLaunchConfigurationsDialog.getTabViewer().setActiveTab(secondTabIndex);

			// Create a new launch config. This one should activate the same tab
			// by default.
			fLaunchConfigurationsDialog.getTabViewer().setInput(createLaunchConfigurationInstance());
		};

		final ILaunchConfigurationTab[] tabs = runOnDialog(setActiveTab);

		assertThat((SpyTab) tabs[0]).withFailMessage("the 1st tab of the other launch configuration shouldn't have been activated").matches(not(SpyTab::isActivated));
		assertThat((SpyTab) tabs[secondTabIndex]).matches(SpyTab::isActivated, "is activated");
	}

	@Test
	@Ignore("https://github.com/eclipse-platform/eclipse.platform/issues/1075")
	public void testOnlyDefaultTabInOtherConfigIsActivated() {
		int overflowTabIndex = Integer.MAX_VALUE;

		ThrowingRunnable<CoreException> setActiveTab = () -> {
			// Create and select launch config
			fLaunchConfigurationsDialog.getTabViewer().setInput(createLaunchConfigurationInstance());

			// Select another tab
			fLaunchConfigurationsDialog.getTabViewer().setActiveTab(overflowTabIndex);

			// Create a new launch config. This one should activate the same tab
			// by default.
			fLaunchConfigurationsDialog.getTabViewer().setInput(createLaunchConfigurationInstance());
		};

		final ILaunchConfigurationTab[] tabs = runOnDialog(setActiveTab);

		assertThat(((SpyTab) tabs[0])).withFailMessage("the 1st tab of the other launch configuration should have been activated").matches(SpyTab::isActivated);

		// All other tabs should not have been initialized
		for (int i = 1; i < tabs.length; i++) {
			assertThat((SpyTab) tabs[i]).withFailMessage("tab %s should not have been initialized", i).matches(not(SpyTab::isInitialized));
		}
	}

	@Test
	public void testOtherTabIsActivated() {
		int secondTabIndex = 1;

		ThrowingRunnable<CoreException> setActiveTab = () -> {
			// Create and select launch config
			fLaunchConfigurationsDialog.getTabViewer().setInput(createLaunchConfigurationInstance());

			// Select another tab
			fLaunchConfigurationsDialog.getTabViewer().setActiveTab(secondTabIndex);
		};

		final ILaunchConfigurationTab[] tabs = runOnDialog(setActiveTab);

		assertThat((SpyTab) tabs[secondTabIndex]).matches(SpyTab::isActivated, "is activated");
	}

	private ILaunchConfigurationWorkingCopy createLaunchConfigurationInstance() throws CoreException {
		return fLaunchConfigurationType.newInstance(null, "MyLaunchConfiguration_" + System.currentTimeMillis());
	}

	private <T extends Exception> ILaunchConfigurationTab[] runOnDialog(ThrowingRunnable<T> runnable) {
		AtomicReference<ILaunchConfigurationTab[]> tabsRef = new AtomicReference<>();
		AtomicReference<Throwable> throwableRef = new AtomicReference<>();

		Display.getCurrent().asyncExec(() -> {
			try {

				runnable.run();

				// I need to store the tabs here because the tab viewer (and all
				// its tabs) are
				// gone as soon as the dialog is closed
				tabsRef.set(fLaunchConfigurationsDialog.getTabs());

			} catch (Throwable e) {
				// neither calling "fail" not throwing an exception will let the
				// test fail so I
				// need to store this and check it outside of the runnable
				throwableRef.set(e);
				// DebugPlugin.log(e);
			} finally {
				fLaunchConfigurationsDialog.close();
			}
		});

		fLaunchConfigurationsDialog.open();

		if (throwableRef.get() != null) {
			throw new AssertionError("An exception occurred while executing the runnable.", throwableRef.get());
		}

		return tabsRef.get();
	}

	protected ILaunchConfigurationTabGroup getLaunchConfigurationTabGroup(ILaunchConfigurationType launchConfigurationType) throws CoreException {
		return LaunchConfigurationPresentationManager.getDefault().getTabGroup(launchConfigurationType, LAUNCH_CONFIG_MODE);
	}

	protected ILaunchConfigurationDialog createLaunchConfigurationDialog() {
		return new LaunchConfigurationsDialog(null, DebugUIPlugin.getDefault().getLaunchConfigurationManager().getLaunchGroup(IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP));
	}

	protected ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}
}

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
package org.eclipse.ui.externaltools.internal.ui;

import java.text.MessageFormat;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.externaltools.internal.model.ExternalToolsPlugin;
import org.eclipse.ui.externaltools.internal.model.IExternalToolConstants;
import org.eclipse.ui.externaltools.internal.ui.BuilderPropertyPage.ErrorConfig;


class BuilderLabelProvider extends LabelProvider {
		private static final String IMG_BUILDER = "icons/full/obj16/builder.gif"; //$NON-NLS-1$;
		private static final String IMG_INVALID_BUILD_TOOL = "icons/full/obj16/invalid_build_tool.gif"; //$NON-NLS-1$
		IDebugModelPresentation debugModelPresentation= DebugUITools.newDebugModelPresentation();

		private Image builderImage = ExternalToolsPlugin.getDefault().getImageDescriptor(IMG_BUILDER).createImage();
		private Image invalidBuildToolImage = ExternalToolsPlugin.getDefault().getImageDescriptor(IMG_INVALID_BUILD_TOOL).createImage();
		
		public String getText(Object element) {
			if (element instanceof ICommand) {
				return getCommandText((ICommand) element);
			} else if (element instanceof ILaunchConfiguration || element instanceof ILaunchConfigurationType) {
				return getDebugModelText(element);
			} else if (element instanceof ErrorConfig) {
				return ExternalToolsUIMessages.getString("BuilderPropertyPage.invalidBuildTool"); //$NON-NLS-1$
			}
			return super.getText(element);
		}
		
		public Image getImage(Object element) {
			if (element instanceof ICommand) {
				return getCommandImage();
			} else if (element instanceof ILaunchConfiguration || element instanceof ILaunchConfigurationType) {
				return getDebugModelImage(element);
			} else if (element instanceof ErrorConfig) {
				return invalidBuildToolImage;
			}
			return super.getImage(element);
		}
		
		public String getCommandText(ICommand command) {
			String builderID = command.getBuilderName();
			return getBuilderName(builderID);
		}
		
		private String getBuilderName(String builderID) {
			// Get the human-readable name of the builder
			IExtension extension = Platform.getPluginRegistry().getExtension(ResourcesPlugin.PI_RESOURCES, ResourcesPlugin.PT_BUILDERS, builderID);
			String builderName;
			if (extension != null) {
				builderName = extension.getLabel();
			} else {
				builderName = MessageFormat.format(ExternalToolsUIMessages.getString("BuilderPropertyPage.missingBuilder"), new Object[] { builderID }); //$NON-NLS-1$
			}
			return builderName;
		}
		
		/**
		 * Returns the image for build commands.
		 * 
		 * @return the build command image
		 */
		public Image getCommandImage() {
			return builderImage;
		}
		
		/**
		 * Returns a text label for the given object from a debug
		 * model presentation.
		 * @param element the element
		 * @return a text label from a debug model presentation
		 */
		public String getDebugModelText(Object element) {
			if (element instanceof ILaunchConfiguration) {
				try {
					String disabledBuilderName= ((ILaunchConfiguration) element).getAttribute(IExternalToolConstants.ATTR_DISABLED_BUILDER, (String)null);
					if (disabledBuilderName != null) {
						//really a disabled builder wrapped as a launch configuration
						return getBuilderName(disabledBuilderName);
					}
				} catch (CoreException e) {
				}
			}
			return debugModelPresentation.getText(element);
		}
		
		/**
		 * Returns an image for the given object from a debug
		 * model presentation.
		 * @param element the element
		 * @return an image from a debug model presentation
		 */
		public Image getDebugModelImage(Object element) {
			if (element instanceof ILaunchConfiguration) {
				try {
					String disabledBuilderName= ((ILaunchConfiguration) element).getAttribute(IExternalToolConstants.ATTR_DISABLED_BUILDER, (String)null);
					if (disabledBuilderName != null) {
						//really a disabled builder wrapped as a launch configuration
						return builderImage;
					}
				} catch (CoreException e) {
				}
			}
			return debugModelPresentation.getImage(element);
		}
			/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#dispose()
		 */
		public void dispose() {
			builderImage.dispose();
			invalidBuildToolImage.dispose();
		}
}
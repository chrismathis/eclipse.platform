/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.core.delta;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Delta change listener that reports changes in an {@link IDeltaTree}.
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is a guarantee neither that this API will
 * work nor that it will remain the same. Please do not use this API without
 * consulting with the Platform/Team team.
 * </p>
 * 
 * @see IDeltaTree
 * 
 * @since 3.2
 */
public interface IDeltaChangeListener {

	/**
	 * The delta contained in the originating tree has changed.
	 * @param event the change event
	 * @param monitor a progress monitor
	 */
	void deltaChanged(IDeltaChangeEvent event, IProgressMonitor monitor);

}

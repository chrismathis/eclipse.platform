package org.eclipse.team.internal.ui.sync;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;

/**
 * A node in a sync tree that represents a changed folder
 * (incoming/outgoing creation or deletion).
 */
public class ChangedTeamContainer extends UnchangedTeamContainer {
	private MergeResource mergeResource;
	
	/**
	 * ChangedVCMContainer constructor
	 */
	public ChangedTeamContainer(SyncCompareInput input, IDiffContainer parent, MergeResource resource, int description) {
		super(input, parent, resource.getResource(), description);
		this.mergeResource = resource;
	}
	/*
	 * Method declared on ITeamNode
	 */
	public boolean canCatchup() {
		// First check for changes to this folder
		int kind = getKind() & Differencer.DIRECTION_MASK;
		if (kind == ITeamNode.INCOMING || kind == Differencer.CONFLICTING) {
			return true;
		}
		return super.canCatchup();
	}
	
	/*
	 * Method declared on ITeamNode
	 */
	public boolean canRelease() {
		if ((getKind() & Differencer.DIRECTION_MASK) == ITeamNode.OUTGOING) {
			return true;
		}
		return super.canRelease();
	}
	
	/*
	 * Method declared on ITeamNode.
	 */
	public int getChangeDirection() {
		return getKind() & Differencer.DIRECTION_MASK;
	}

	public String getName() {
		return mergeResource.getName();
	}

	/*
	 * Method declared on IDiffContainer
	 */
	public void removeToRoot(IDiffElement child) {
		// Don't want to remove empty changed containers
		remove(child);
	}

	/**
	 * For debugging purposes only.
	 */
	public String toString() {
		return "ChangedTeamContainer(" + getResource().getName() + ")";
	}
}

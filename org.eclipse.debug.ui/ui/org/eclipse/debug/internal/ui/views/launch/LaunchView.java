package org.eclipse.debug.internal.ui.views.launch;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.DelegatingModelPresentation;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.views.AbstractDebugEventHandlerView;
import org.eclipse.debug.internal.ui.views.DebugUIViewsMessages;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ISourcePresentation;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.SubContributionItem;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveListener;
import org.eclipse.ui.IReusableEditor;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.PropertyDialogAction;
import org.eclipse.ui.texteditor.ITextEditor;

public class LaunchView extends AbstractDebugEventHandlerView implements ISelectionChangedListener, IPerspectiveListener, IPageListener, IPropertyChangeListener {
	
	/**
	 * A marker for the source selection and icon for an
	 * instruction pointer.  This marker is transient.
	 */
	private IMarker fInstructionPointer;
	private boolean fShowingMarker = false;
	
	// marker attributes
	private final static String[] fgStartEnd = 
		new String[] {IMarker.CHAR_START, IMarker.CHAR_END};
		
	private final static String[] fgLineStartEnd = 
		new String[] {IMarker.LINE_NUMBER, IMarker.CHAR_START, IMarker.CHAR_END};	
		
	/**
	 * Cache of the stack frame that source was displayed
	 * for.
	 */
	private IStackFrame fStackFrame = null;
	
	/**
	 * Cache of the editor input used to display source
	 */
	private IEditorInput fEditorInput = null;
	
	/**
	 * Cache of the editor id used to display source
	 */
	private String fEditorId = null;
	
	/**
	 * Whether this view is in the active page of a perspective.
	 */
	private boolean fIsActive = true; 	
	
	/**
	 * Editor to reuse
	 */
	private IEditorPart fEditor = null;
	
	/**
	 * The restored editor index of the editor to re-use
	 */
	private int fEditorIndex = -1;
	
	/**
	 * Whether to re-use editors
	 */
	private boolean fReuseEditor = DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugUIConstants.PREF_REUSE_EDITOR);
	
	/**
	 * Creates a launch view and an instruction pointer marker for the view
	 */
	public LaunchView() {
		try {
			fInstructionPointer = ResourcesPlugin.getWorkspace().getRoot().createMarker(IInternalDebugUIConstants.INSTRUCTION_POINTER);
			DebugUIPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this);
		} catch (CoreException e) {
			DebugUIPlugin.log(e);
		}
	}
	
	/**
	 * @see AbstractDebugView#getHelpContextId()
	 */
	protected String getHelpContextId() {
		return IDebugHelpContextIds.DEBUG_VIEW;
	}
	
	/**
	 * @see AbstractDebugView#createActions()
	 */
	protected void createActions() {
		setAction("Properties", new PropertyDialogAction(getSite().getWorkbenchWindow().getShell(), getSite().getSelectionProvider())); //$NON-NLS-1$
				
		// submit an async exec to update the selection once the
		// view has been created - i.e. auto-expand and select the
		// suspended thread on creation. (Done here, because the
		// viewer needs to be set).
		Runnable r = new Runnable() {
			public void run() {
				initializeSelection();
			}
		};
		asyncExec(r);		
		
	}

	/**
	 * @see AbstractDebugView#createViewer(Composite)
	 */
	protected Viewer createViewer(Composite parent) {
		LaunchViewer lv = new LaunchViewer(parent);
		lv.addSelectionChangedListener(this);
		lv.setContentProvider(createContentProvider());
		lv.setLabelProvider(new DelegatingModelPresentation());
		lv.setUseHashlookup(true);
		// add my viewer as a selection provider, so selective re-launch works
		getSite().setSelectionProvider(lv);
		lv.setInput(DebugPlugin.getDefault().getLaunchManager());
		setEventHandler(new LaunchViewEventHandler(this));
		
		// determine if active
		setActive(getSite().getPage().findView(getSite().getId()) != null);
		
		return lv;
	}
	
	/**
	 * Select the first stack frame in a suspended thread,
	 * if any.
	 */
	protected void initializeSelection() {
		if (!isAvailable()) {
			return;
		}
		TreeViewer tv = (TreeViewer)getViewer();
		tv.expandToLevel(2);
		Object[] elements = tv.getExpandedElements();
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] instanceof ILaunch) {
				IStackFrame frame = findFrame((ILaunch)elements[i]);
				if (frame != null) {
					autoExpand(frame, false, true);
				}
			}
		}
	}
	
	/**
	 * Returns the first stack frame in the first suspended
	 * thread of the given launch, or <code>null</code> if
	 * none.
	 * 
	 * @param launch a launch in this view
	 * @return stack frame or <code>null</code>
	 */
	protected IStackFrame findFrame(ILaunch launch) {
		IDebugTarget target = launch.getDebugTarget();
		if (target != null) {
			try {
				IThread[] threads = target.getThreads();
				for (int i = 0; i < threads.length; i++) {
					if (threads[i].isSuspended()) {
						return threads[i].getTopStackFrame();
					}
				}
			} catch (DebugException e) {
				DebugUIPlugin.log(e);
			}
		}
		return null;
	}
	
	/**
	 * @see IViewPart#init(IViewSite)
	 */
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		site.getPage().addPartListener(this);
		site.getWorkbenchWindow().addPageListener(this);
		site.getWorkbenchWindow().addPerspectiveListener(this);
	}

	/**
	 * @see IViewPart#init(org.eclipse.ui.IViewSite, org.eclipse.ui.IMemento)
	 */
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		site.getPage().addPartListener(this);
		site.getWorkbenchWindow().addPageListener(this);
		site.getWorkbenchWindow().addPerspectiveListener(this);
		if (fReuseEditor && memento != null) {
			String index = memento.getString(IDebugUIConstants.PREF_REUSE_EDITOR);
			if (index != null) {
				try {
					fEditorIndex = Integer.parseInt(index);
				} catch (NumberFormatException e) {
					DebugUIPlugin.log(e);
				}
			}
		}
	}
		
	/**
	 * @see AbstractDebugView#configureToolBar(IToolBarManager)
	 */
	protected void configureToolBar(IToolBarManager tbm) {
		tbm.add(new Separator(IDebugUIConstants.THREAD_GROUP));
		tbm.add(new Separator(IDebugUIConstants.STEP_GROUP));
		tbm.add(new GroupMarker(IDebugUIConstants.STEP_INTO_GROUP));
		tbm.add(new GroupMarker(IDebugUIConstants.STEP_OVER_GROUP));
		tbm.add(new GroupMarker(IDebugUIConstants.STEP_RETURN_GROUP));
		tbm.add(new GroupMarker(IDebugUIConstants.EMPTY_STEP_GROUP));
		tbm.add(new Separator(IDebugUIConstants.RENDER_GROUP));
	}	

	/**
	 * @see IWorkbenchPart#dispose()
	 */
	public void dispose() {
		if (getViewer() != null) {
			getViewer().removeSelectionChangedListener(this);
		}
		getSite().getPage().removePartListener(this);
		getSite().getWorkbenchWindow().removePerspectiveListener(this);
		getSite().getWorkbenchWindow().removePageListener(this);
		setEditorId(null);
		setEditorInput(null);
		setStackFrame(null);
		DebugUIPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(this);
		super.dispose();
	}
	
	/**
	 * Creates and returns the content provider to use for
	 * the viewer of this view.
	 */
	protected IStructuredContentProvider createContentProvider() {
		return new LaunchViewContentProvider();
	}
	
	/**
	 * The selection has changed in the viewer. Show the
	 * associated source code if it is a stack frame.
	 * 
	 * @see ISelectionChangedListener#selectionChanged(SelectionChangedEvent)
	 */
	public void selectionChanged(SelectionChangedEvent event) {
		updateObjects();
		showMarkerForCurrentSelection();
	}
			
	/**
	 * @see IDoubleClickListener#doubleClick(DoubleClickEvent)
	 */
	public void doubleClick(DoubleClickEvent event) {
		ISelection selection= event.getSelection();
		if (!(selection instanceof IStructuredSelection)) {
			return;
		}
		IStructuredSelection ss= (IStructuredSelection)selection;
		Object o= ss.getFirstElement();
		if (o instanceof IStackFrame) {
			return;
		} 
		TreeViewer tViewer= (TreeViewer)getViewer();
		boolean expanded= tViewer.getExpandedState(o);
		tViewer.setExpandedState(o, !expanded);
	}
		
	/**
	 * @see IPerspectiveListener#perspectiveActivated(IWorkbenchPage, IPerspectiveDescriptor)
	 */
	public void perspectiveActivated(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
		setActive(page.findView(getSite().getId()) != null);
		updateObjects();
		showMarkerForCurrentSelection();
		if (isActive()) {
			asyncExec(new Runnable() {
				public void run() {
					updateDebugActionSetAccelerators();
				}
			});

		}
	}

	/**
	 * @see IPerspectiveListener#perspectiveChanged(IWorkbenchPage, IPerspectiveDescriptor, String)
	 */
	public void perspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective, String changeId) {
			setActive(page.findView(getSite().getId()) != null);
	}

	/**
	 * @see IPageListener#pageActivated(IWorkbenchPage)
	 */
	public void pageActivated(IWorkbenchPage page) {
		if (getSite().getPage().equals(page)) {
			setActive(true);
			updateObjects();
			showMarkerForCurrentSelection();
		}
	}
	
	/**
	 * @see org.eclipse.ui.IPartListener#partClosed(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partClosed(IWorkbenchPart part) {
		if (part.equals(fEditor)) {
			fEditor = null;
		}
	}
	
	/**
	 * Workaround for bug 9082
	 */
	protected void updateDebugActionSetAccelerators() {
		IWorkbenchWindow window= DebugUIPlugin.getActiveWorkbenchWindow();
		if (window instanceof ApplicationWindow) {
			ApplicationWindow appWindow= (ApplicationWindow)window;
			IMenuManager manager= appWindow.getMenuBarManager();
			IContributionItem actionSetItem= manager.findUsingPath("org.eclipse.ui.run"); //$NON-NLS-1$
			if (actionSetItem instanceof SubContributionItem) {
				IContributionItem item= ((SubContributionItem)actionSetItem).getInnerItem();
				if (item instanceof IMenuManager) {
					//force the accelerators to be updated
					((IMenuManager)item).update(true);
				}
			}
		}
	}

	/**
	 * @see IPageListener#pageClosed(IWorkbenchPage)
	 */
	public void pageClosed(IWorkbenchPage page) {
	}

	/**
	 * @see IPageListener#pageOpened(IWorkbenchPage)
	 */
	public void pageOpened(IWorkbenchPage page) {
	}
		
	/**
	 * Returns the configured instruction pointer.
	 * Selection is based on the line number OR char start and char end.
	 */
	protected IMarker getInstructionPointer(final int lineNumber, final int charStart, final int charEnd) {
		
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		IWorkspaceRunnable runnable= new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				if (lineNumber == -1) {
					fInstructionPointer.setAttributes(fgStartEnd, 
						new Object[] {new Integer(charStart), new Integer(charEnd)});
				} else {
					fInstructionPointer.setAttributes(fgLineStartEnd, 
						new Object[] {new Integer(lineNumber), new Integer(charStart), new Integer(charEnd)});
				}
			}
		};
		
		try {
			workspace.run(runnable, null);
		} catch (CoreException ce) {
			DebugUIPlugin.log(ce);
		}
		
		return fInstructionPointer;
	}		
	
	/**
	 * Opens a marker for the current selection if it is a stack frame.
	 * If the current selection is a thread, deselection occurs.
	 * Otherwise, nothing will happen.
	 */
	protected void showMarkerForCurrentSelection() {
		// ensure this view is visible in the active page
		if (!isActive()) {
			return;
		}		
		if (!fShowingMarker) {
			try {
				fShowingMarker = true;
				ISelection selection= getViewer().getSelection();
				Object obj= null;
				if (selection instanceof IStructuredSelection) {
					obj= ((IStructuredSelection) selection).getFirstElement();
				}
				if (!(obj instanceof IStackFrame)) {
					return;
				}
				
				IStackFrame stackFrame= (IStackFrame) obj;
				if (!stackFrame.equals(getStackFrame())) {
					setStackFrame(stackFrame);
					lookupEditorInput();
				}
				
				if (getEditorInput() != null && getEditorId() != null) {
					int lineNumber= 0;
					int start = -1;
					int end = -1;
					try {
						lineNumber= stackFrame.getLineNumber();
						start= stackFrame.getCharStart();
						end= stackFrame.getCharEnd();
					} catch (DebugException de) {
						DebugUIPlugin.log(de);
					}
					openEditorAndSetMarker(getEditorInput(), getEditorId(), lineNumber, start, end);
				}
			} finally {
				fShowingMarker= false;
			}
		}
	}
	
	/**
	 * Translate to an editor input using the source presentation
	 * provided by the source locator, or the default debug model
     * presentation.
     */
	private void lookupEditorInput() {
		setEditorId(null);
		setEditorInput(null);
		Object sourceElement= null;
		IStackFrame stackFrame= getStackFrame();
		ILaunch launch = stackFrame.getLaunch();
		if (launch == null) {
			return;
		}
		ISourceLocator locator= launch.getSourceLocator();
		if (locator == null) {
			return;
		}
		sourceElement = locator.getSourceElement(stackFrame);
		if (sourceElement == null) {
			return;
		}
		
		ISourcePresentation presentation = null;
		if (locator instanceof ISourcePresentation) {
			presentation = (ISourcePresentation)locator;
		} else {
			presentation = getPresentation(stackFrame.getModelIdentifier());
		}
		IEditorInput editorInput= null;
		String editorId= null;
		if (presentation != null) {
			editorInput= presentation.getEditorInput(sourceElement);
		}
		
		if (editorInput != null) {
			editorId= presentation.getEditorId(editorInput, sourceElement);
		}
		setEditorInput(editorInput);
		setEditorId(editorId);
	}

	/**
	 * Get the active window and open/bring to the front an editor on the source element.
	 * Selection is based on the line number OR the char start and end.
	 */
	protected void openEditorAndSetMarker(IEditorInput input, String editorId, int lineNumber, int charStart, int charEnd) {
		IWorkbenchWindow dwindow= getSite().getWorkbenchWindow();
		if (dwindow == null) {
			return;
		}
		
		IWorkbenchPage page= dwindow.getActivePage();
		if (page == null) {
			return;
		}
		
		if (fEditorIndex >= 0) {
			// first restoration of editor re-use
			IEditorReference[] refs = page.getEditorReferences();
			if (fEditorIndex < refs.length) {
				fEditor = refs[fEditorIndex].getEditor(false);
			}
			fEditorIndex = -1;
		}

		//  if there is an editor in the debug page with the newInput
		//     page bringToTop(editorAlreadyOpened)
		//  else if editorToBeReused is null or 
		//    if editorToBeReused is pinned or 
		//    if editorToBeReused is dirty or 
		//    if keep editors opened (debug preference)
		//        editorToBeReused = page openEditor
		//  else if editorToBeReused is instance of IReusableEditor
		//        editorToBeReused.setInput(newInput)
		//  else
		//        page close editorToBeReused
		//        editorToBeReused = page openEditor
        		
		IEditorPart editor = null;
		if (fReuseEditor) {
			editor = page.getActiveEditor();
			if (editor != null) {
				if (!editor.getEditorInput().equals(input)) {
					editor = null;
				}
			}
			if (editor == null) {
				IEditorReference[] refs = page.getEditorReferences();
				for (int i = 0; i < refs.length; i++) {
					IEditorPart refEditor= refs[i].getEditor(false);
					if (input.equals(refEditor.getEditorInput())) {
						editor = refEditor;
						page.bringToTop(editor);
						break;
					}
				}	
			}
			if (editor == null) {
				if (fEditor == null || fEditor.isDirty() || page.isEditorPinned(fEditor)) {
					editor = openEditor(page, input, editorId, false);
					fEditor = editor;
				} else if (fEditor instanceof IReusableEditor && editorId.equals(fEditor.getSite().getId())) {
					((IReusableEditor)fEditor).setInput(input);
					editor = fEditor;
				} else {
					page.closeEditor(fEditor, false);
					editor = openEditor(page, input, editorId, false);
					fEditor = editor;
				}
			}		
		} else {
			editor = openEditor(page, input, editorId, false);
		}
		
		
		if (editor != null && (lineNumber >= 0 || charStart >= 0)) {
			//have an editor and either a lineNumber or a starting character
			IMarker marker= getInstructionPointer(lineNumber, charStart, charEnd);
			editor.gotoMarker(marker);
		}
	}
	
	protected IEditorPart openEditor(IWorkbenchPage page, IEditorInput input, String id, boolean activate) {
		try {
			return page.openEditor(input, id, false);
		} catch (PartInitException e) {
			DebugUIPlugin.errorDialog(DebugUIPlugin.getDefault().getShell(), 
				DebugUIViewsMessages.getString("LaunchView.Error_1"),  //$NON-NLS-1$
				DebugUIViewsMessages.getString("LaunchView.Exception_occurred_opening_editor_for_debugger._2"),  //$NON-NLS-1$
				e);
		}					
		return null;
	}

	/**
	 * Deselects any source in the active editor that was 'programmatically' selected by
	 * the debugger.
	 */
	public void clearSourceSelection() {		
		// Get the active editor
		IEditorPart editor= getSite().getPage().getActiveEditor();
		if (!(editor instanceof ITextEditor)) {
			return;
		}
		ITextEditor textEditor= (ITextEditor)editor;
		
		// Get the current text selection in the editor.  If there is none, 
		// then there's nothing to do
		ITextSelection textSelection= (ITextSelection)textEditor.getSelectionProvider().getSelection();
		if (textSelection.isEmpty()) {
			return;
		}
		int startChar= textSelection.getOffset();
		int endChar= startChar + textSelection.getLength() - 1;
		int startLine= textSelection.getStartLine();
		
		// Check to see if the current selection looks the same as the last 'programmatic'
		// selection in fInstructionPointer.  If not, it must be a user selection, which
		// we leave alone.  In practice, we can leave alone any user selections on other lines,
		// but if the user makes a selection on the same line as the last programmatic selection,
		// it will get cleared.
		int lastCharStart= fInstructionPointer.getAttribute(IMarker.CHAR_START, -1);
		if (lastCharStart == -1) {
			// subtract 1 since editor is 0-based
			if (fInstructionPointer.getAttribute(IMarker.LINE_NUMBER, -1) - 1 != startLine) {
				return;
			}
		} else {
			int lastCharEnd= fInstructionPointer.getAttribute(IMarker.CHAR_END, -1);
			if ((lastCharStart != startChar) || (lastCharEnd != endChar)) {
				return;			     
			}
		}
		
		ITextSelection nullSelection= getNullSelection(startLine, startChar);
		textEditor.getSelectionProvider().setSelection(nullSelection);		
	}

	/**
	 * Creates and returns an ITextSelection that is a zero-length selection located at the
	 * start line and start char.
	 */
	protected ITextSelection getNullSelection(final int startLine, final int startChar) {
		return new ITextSelection() {
			public int getStartLine() {
				return startLine;
			}
			public int getEndLine() {
				return startLine;
			}
			public int getOffset() {
				return startChar;
			}
			public String getText() {
				return ""; //$NON-NLS-1$
			}
			public int getLength() {
				return 0;
			}
			public boolean isEmpty() {
				return true;
			}
		};
	}
	
	/**
	 * @see AbstractDebugView#fillContextMenu(IMenuManager)
	 */
	protected void fillContextMenu(IMenuManager menu) {
		
		menu.add(new Separator(IDebugUIConstants.EMPTY_EDIT_GROUP));
		menu.add(new Separator(IDebugUIConstants.EDIT_GROUP));
		menu.add(new Separator(IDebugUIConstants.EMPTY_STEP_GROUP));
		menu.add(new Separator(IDebugUIConstants.STEP_GROUP));
		menu.add(new GroupMarker(IDebugUIConstants.STEP_INTO_GROUP));
		menu.add(new GroupMarker(IDebugUIConstants.STEP_OVER_GROUP));
		menu.add(new GroupMarker(IDebugUIConstants.STEP_RETURN_GROUP));
		menu.add(new Separator(IDebugUIConstants.EMPTY_THREAD_GROUP));
		menu.add(new Separator(IDebugUIConstants.THREAD_GROUP));
		menu.add(new Separator(IDebugUIConstants.EMPTY_LAUNCH_GROUP));
		menu.add(new Separator(IDebugUIConstants.LAUNCH_GROUP));
		menu.add(new Separator(IDebugUIConstants.EMPTY_RENDER_GROUP));
		menu.add(new Separator(IDebugUIConstants.RENDER_GROUP));
		menu.add(new Separator(IDebugUIConstants.PROPERTY_GROUP));
		PropertyDialogAction action = (PropertyDialogAction)getAction("Properties"); //$NON-NLS-1$
		action.setEnabled(action.isApplicableForSelection());
		menu.add(action);
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}	
	
	/**
	 * Auto-expand and select the given element - must be called in UI thread.
	 * This is used to implement auto-expansion-and-select on a SUSPEND event.
	 */
	public void autoExpand(Object element, boolean refreshNeeded, boolean selectNeeded) {
		Object selectee = element;
		Object[] children= null;
		if (element instanceof IThread) {
			if (!refreshNeeded) {
				refreshNeeded= threadRefreshNeeded((IThread)element);
			}
			// try the top stack frame
			try {
				selectee = ((IThread)element).getTopStackFrame();
			} catch (DebugException de) {
			}
			if (selectee == null) {
				selectee = element;
			}
		} else if (element instanceof ILaunch) {
			IDebugTarget dt = ((ILaunch)element).getDebugTarget();
			if (dt != null) {
				selectee= dt;
				try {
					children= dt.getThreads();
				} catch (DebugException de) {
					DebugUIPlugin.log(de);
				}
			}
		}
		if (refreshNeeded) {
			//ensures that the child item exists in the viewer widget
			//set selection only works if the child exists
			getStructuredViewer().refresh(element);
		}
		if (selectNeeded) {
			getViewer().setSelection(new StructuredSelection(selectee), true);
		}
		if (children != null && children.length > 0) {
			//reveal the thread children of a debug target
			getStructuredViewer().reveal(children[0]);
		}
	}
	
	/**
	 * Returns whether the given thread needs to
	 * be refreshed in the tree.
	 * 
	 * The tree needs to be refreshed if the
	 * underlying model objects (IStackFrame) under the given thread
	 * differ from those currently displayed in the tree.
	 */
	protected boolean threadRefreshNeeded(IThread thread) {
		LaunchViewer viewer= (LaunchViewer)getStructuredViewer();
		ILaunch launch= thread.getLaunch();
		TreeItem[] launches= viewer.getTree().getItems();
		for (int i = 0; i < launches.length; i++) {
			if (launches[i].getData() == launch) {
				IDebugTarget target= thread.getDebugTarget();
				TreeItem[] targets= launches[i].getItems();
				for (int j = 0; j < targets.length; j++) {
					if (targets[j].getData() == target) {
						TreeItem[] threads= targets[j].getItems();
						for (int k = 0; k < threads.length; k++) {
							if (threads[k].getData() == thread) {
								IStackFrame[] frames= null;
								try {
									frames = thread.getStackFrames();
								} catch (DebugException exception) {
									return true;
								}
								TreeItem[] treeFrames= threads[k].getItems();
								for (int l= 0, numFrames= treeFrames.length; l < numFrames; l++) {
									if (treeFrames[l].getData() != frames[l]) {
										return true;
									}
								}
								break;
							}
						}
						break;
					}
				}
				break;
			}
		}
		return false;
	}
	
	/**
	 * Returns the last stack frame that source was retrieved
	 * for. Used to avoid source lookups for the same stack
	 * frame when stepping.
	 * 
	 * @return stack frame, or <code>null</code>
	 */
	protected IStackFrame getStackFrame() {
		return fStackFrame;
	}	
	
	/**
	 * Sets the last stack frame that source was retrieved
	 * for. Used to avoid source lookups for the same stack
	 * frame when stepping. Setting the stack frame to <code>null</code>
	 * effectively forces a source lookup.
	 * 
	 * @param frame The stack frame or <code>null</code>
	 */
	protected void setStackFrame(IStackFrame frame) {
		fStackFrame= frame;
	}	
	
	/**
	 * Sets the editor input that was resolved for the
	 * source display.
	 * 
	 * @param editorInput editor input
	 */
	private void setEditorInput(IEditorInput editorInput) {
		fEditorInput = editorInput;
	}
	
	/**
	 * Returns the editor input that was resolved for the
	 * source display.
	 * 
	 * @return editor input
	 */
	protected IEditorInput getEditorInput() {
		return fEditorInput;
	}	
	
	/**
	 * Sets the id of the editor opened when displaying
	 * source.
	 * 
	 * @param editorId editor id
	 */
	private void setEditorId(String editorId) {
		fEditorId = editorId;
	}
	
	/**
	 * Returns the id of the editor opened when displaying
	 * source.
	 * 
	 * @return editor id
	 */
	protected String getEditorId() {
		return fEditorId;
	}	
	
	/**
	 * Sets whether this view is in the active page of a
	 * perspective. Since a page can have more than one
	 * perspective, this view only show's source when in
	 * the active perspective/page.
	 * 
	 * @param active whether this view is in the active page of a
	 * perspective
	 */
	protected void setActive(boolean active) {
		fIsActive = active;
	} 

	/**
	 * Returns whether this view is in the active page of
	 * the active perspective and has been fully created.
	 * 
	 * @return whether this view is in the active page of
	 * the active perspective and has been fully created.
	 */
	protected boolean isActive() {
		return fIsActive && getViewer() != null;
	}
	
	/**
	 * @see IPropertyChangeListener#propertyChange(PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty() == IDebugUIConstants.PREF_REUSE_EDITOR) {
			fReuseEditor = DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugUIConstants.PREF_REUSE_EDITOR);
		}
	}

	/**
	 * @see IViewPart#saveState(IMemento)
	 */
	public void saveState(IMemento memento) {
		super.saveState(memento);
		if (fReuseEditor && fEditor != null) {
			IWorkbenchWindow dwindow= getSite().getWorkbenchWindow();
			if (dwindow == null) {
				return;
			}	
			IWorkbenchPage page= dwindow.getActivePage();
			if (page == null) {
				return;
			}
			IEditorReference[] refs = page.getEditorReferences();
			int index = -1;
			for (int i = 0; i < refs.length; i++) {
				if (fEditor.equals(refs[i].getEditor(false))) {
					index = i;
					break;
				}
			}
			if (index >= 0) {	
				memento.putString(IDebugUIConstants.PREF_REUSE_EDITOR, Integer.toString(index));
			}
		}
	}

}
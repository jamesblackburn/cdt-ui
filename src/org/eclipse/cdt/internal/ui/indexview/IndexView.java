/*******************************************************************************
 * Copyright (c) 2005 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX - Initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.internal.ui.indexview;

import java.util.ArrayList;

import org.eclipse.cdt.core.dom.IPDOM;
import org.eclipse.cdt.core.dom.PDOM;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.ICompositeType;
import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IVariable;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICModel;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.resources.FileStorage;
import org.eclipse.cdt.internal.core.pdom.PDOMDatabase;
import org.eclipse.cdt.internal.core.pdom.PDOMUpdator;
import org.eclipse.cdt.internal.core.pdom.db.IBTreeVisitor;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMBinding;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMLinkage;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMMember;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMMemberOwner;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMName;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMNode;
import org.eclipse.cdt.internal.ui.util.EditorUtility;
import org.eclipse.cdt.internal.ui.viewsupport.CElementImageProvider;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * @author Doug Schaefer
 *
 */
public class IndexView extends ViewPart implements PDOMDatabase.IListener {

	private TreeViewer viewer;
//	private DrillDownAdapter drillDownAdapter;
	private IndexAction rebuildAction;
	private IndexAction openDefinitionAction;
	
	private static class Counter implements IBTreeVisitor {
		int count;
		PDOMDatabase pdom;
		public Counter(PDOMDatabase pdom) {
			this.pdom = pdom;
		}
		public int compare(int record) throws CoreException {
			return 1;
		}
		public boolean visit(int record) throws CoreException {
			if (record != 0 && ! PDOMBinding.isOrphaned(pdom, record))
				++count;
			return true;
		}
	}

	private static class Children implements IBTreeVisitor {
		final PDOMDatabase pdom;
		final PDOMBinding[] bindings;
		int index;
		public Children(PDOMDatabase pdom, PDOMBinding[] bindings) {
			this.pdom = pdom;
			this.bindings = bindings;
		}
		public int compare(int record) throws CoreException {
			return 1;
		};
		public boolean visit(int record) throws CoreException {
			if (record == 0 || PDOMBinding.isOrphaned(pdom, record))
				return true;
			
			bindings[index++] = pdom.getBinding(record);
			return true;
		};
	}

	private class IndexLazyContentProvider implements ILazyTreeContentProvider {

		public Object getParent(Object element) {
			return null;
		}

		public void updateElement(Object parent, int index) {
			try {
				if (parent instanceof ICModel) {
					ICModel model = (ICModel)parent;
					ICProject[] cprojects = model.getCProjects();
					int n = -1;
					for (int i = 0; i < cprojects.length; ++i) {
						ICProject cproject = cprojects[i];
						PDOMDatabase pdom = (PDOMDatabase)PDOM.getPDOM(cproject.getProject());
						if (pdom != null)
							++n;
						if (n == index) {
							viewer.replace(parent, index, cproject);
							int nl = 0;
							for (PDOMLinkage linkage = pdom.getFirstLinkage(); linkage != null; linkage = linkage.getNextLinkage())
								++nl;
							viewer.setChildCount(cproject, nl);
							return;
						}
					}
				} else if (parent instanceof ICProject) {
					ICProject cproject = (ICProject)parent;
					PDOMDatabase pdom = (PDOMDatabase)PDOM.getPDOM(cproject.getProject());
					PDOMLinkage linkage = pdom.getFirstLinkage();
					if (linkage == null)
						return;
					for (int n = 0; n < index; ++n) {
						linkage = linkage.getNextLinkage();
						if (linkage == null)
							return;
					}
					LinkageCache linkageCache = new LinkageCache(pdom, linkage);
					viewer.replace(parent, index, linkageCache);
					viewer.setChildCount(linkageCache, linkageCache.getCount());
				} else if (parent instanceof LinkageCache) {
					LinkageCache linkageCache = (LinkageCache)parent;
					PDOMBinding binding = linkageCache.getItem(index);
					if (binding != null) {
						viewer.replace(parent, index, binding);
						if (binding instanceof PDOMMemberOwner) {
							PDOMMemberOwner owner = (PDOMMemberOwner)binding;
							viewer.setChildCount(binding, owner.getNumMembers());
						} else
							viewer.setChildCount(binding, 0);
					}
				} else if (parent instanceof PDOMMemberOwner) {
					PDOMMemberOwner owner = (PDOMMemberOwner)parent;
					PDOMMember member = owner.getMember(index);
					viewer.replace(parent, index, member);
					viewer.setChildCount(member, 0);
				}
			} catch (CoreException e) {
				CUIPlugin.getDefault().log(e);
			}
		}

		public void dispose() {
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

	}
	
	private class IndexContentProvider implements ITreeContentProvider {

		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof ICProject) {
				try {
					PDOMDatabase pdom = (PDOMDatabase)PDOM.getPDOM(((ICProject)parentElement).getProject());
					int n = 0;
					for (PDOMLinkage linkage = pdom.getFirstLinkage(); linkage != null; linkage = linkage.getNextLinkage())
						++n;
					PDOMLinkage[] linkages = new PDOMLinkage[n];
					int i = 0;
					for (PDOMLinkage linkage = pdom.getFirstLinkage(); linkage != null; linkage = linkage.getNextLinkage())
						linkages[i++] = linkage;
					return linkages;
				} catch (CoreException e) {
					CUIPlugin.getDefault().log(e);
				}
			} else if (parentElement instanceof PDOMLinkage) {
				try {
					PDOMLinkage linkage = (PDOMLinkage)parentElement;
					PDOMDatabase pdom = linkage.getPDOM();
					Counter counter = new Counter(pdom);
					linkage.getIndex().visit(counter);
					PDOMBinding[] bindings = new PDOMBinding[counter.count];
					Children children = new Children(pdom, bindings);
					linkage.getIndex().visit(children);
					return bindings;
				} catch (CoreException e) {
					CUIPlugin.getDefault().log(e);
				} 
			} else if (parentElement instanceof PDOMMemberOwner) {
				try {
					PDOMMemberOwner owner = (PDOMMemberOwner)parentElement;
					int n = 0;
					for (PDOMMember member = owner.getFirstMember(); member != null; member = member.getNextMember())
						++n;
					PDOMMember[] members = new PDOMMember[n];
					int i = 0;
					for (PDOMMember member = owner.getFirstMember(); member != null; member = member.getNextMember())
						members[i++] = member;
					return members;
				} catch (CoreException e) {
					CUIPlugin.getDefault().log(e);
				}
			}
			return new Object[0];
		}

		public Object getParent(Object element) {
			return null;
		}

		public boolean hasChildren(Object element) {
			if (element instanceof ICProject) {
				IPDOM ipdom = PDOM.getPDOM(((ICProject)element).getProject());
				if (ipdom == null || !(ipdom instanceof PDOMDatabase))
					return false;
				
				try {
					PDOMDatabase pdom = (PDOMDatabase)ipdom;
					return pdom.getFirstLinkage() != null;
				} catch (CoreException e) {
					CUIPlugin.getDefault().log(e);
				}
			} else if (element instanceof PDOMLinkage) {
				return true;
			} else if (element instanceof PDOMMemberOwner) {
				try {
					PDOMMemberOwner owner = (PDOMMemberOwner)element;
					return owner.getFirstMember() != null;
				} catch (CoreException e) {
					CUIPlugin.getDefault().log(e);
				}
			}
			
			return false;
		}

		public Object[] getElements(Object inputElement) {
			try {
				if (inputElement instanceof ICModel) {
			
					ICModel model = (ICModel)inputElement;
					return model.getCProjects();
				}
			} catch (CModelException e) { }
			
			return new Object[0];
		}

		public void dispose() {
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
		
	}
	
	private class IndexLabelProvider extends LabelProvider {
		public String getText(Object element) {
			if (element == null) {
				return "null :(";
			} else if (element instanceof PDOMNode) {
				try {
					return ((PDOMNode)element).getName();
				} catch (CoreException e) {
					return e.getMessage();
				}
			} else if (element instanceof LinkageCache) {
				try {
					return ((LinkageCache)element).getName();
				} catch (CoreException e) {
					return e.getMessage();
				}
			} else
				return super.getText(element);
		}
		
		public Image getImage(Object element) {
			if (element instanceof IVariable)
				return CUIPlugin.getImageDescriptorRegistry().get(
						CElementImageProvider.getVariableImageDescriptor());
			else if (element instanceof IFunction)
				return CUIPlugin.getImageDescriptorRegistry().get(
						CElementImageProvider.getFunctionImageDescriptor());
			else if (element instanceof ICPPClassType)
				return CUIPlugin.getImageDescriptorRegistry().get(
						CElementImageProvider.getClassImageDescriptor());
			else if (element instanceof ICompositeType)
				return CUIPlugin.getImageDescriptorRegistry().get(
						CElementImageProvider.getStructImageDescriptor());
			else if (element instanceof IBinding)
				return PlatformUI.getWorkbench().getSharedImages().getImage(
						ISharedImages.IMG_OBJ_ELEMENT);
			else if (element instanceof ICProject)
				return PlatformUI.getWorkbench().getSharedImages().getImage(
						IDE.SharedImages.IMG_OBJ_PROJECT);
			else
				return PlatformUI.getWorkbench().getSharedImages().getImage(
						ISharedImages.IMG_OBJ_ELEMENT);
		}
		
	}
	
	public void createPartControl(Composite parent) {
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
//		viewer = new TreeViewer(parent, SWT.VIRTUAL | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
//		drillDownAdapter = new DrillDownAdapter(viewer);
		viewer.setContentProvider(new IndexContentProvider());
		viewer.setLabelProvider(new IndexLabelProvider());
		
		ICModel model = CoreModel.getDefault().getCModel();
		viewer.setInput(model);
		try {
			ICProject[] cprojects = model.getCProjects();
			int n = 0;
			for (int i = 0; i < cprojects.length; ++i) {
				PDOMDatabase pdom = (PDOMDatabase)PDOM.getPDOM(cprojects[i].getProject()); 
				if (pdom != null) {
					++n;
					pdom.addListener(this);
				}
			}
			viewer.setChildCount(model, n);
		} catch (CModelException e) {
			CUIPlugin.getDefault().log(e);
		}
		
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
		
		// Menu
        MenuManager menuMgr = new MenuManager();
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            private void hideMenuItems(IMenuManager manager) {
            }

            public void menuAboutToShow(IMenuManager manager) {
                IndexView.this.fillContextMenu(manager);
                hideMenuItems(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuMgr, viewer);
	}
	
	private abstract static class IndexAction extends Action {
		public abstract boolean valid();
	}
	
	private void makeActions() {
		rebuildAction = new IndexAction() {
			public void run() {
				ISelection selection = viewer.getSelection();
				if (!(selection instanceof IStructuredSelection))
					return;
				
				Object[] objs = ((IStructuredSelection)selection).toArray();
				for (int i = 0; i < objs.length; ++i) {
					if (!(objs[i] instanceof ICProject))
						continue;
					
					ICProject cproject = (ICProject)objs[i];
					try {
						PDOM.deletePDOM(cproject.getProject());
						PDOMUpdator job = new PDOMUpdator(cproject, null);
						job.schedule();
					} catch (CoreException e) {
						CUIPlugin.getDefault().log(e);
					}
				}
			}
			public boolean valid() {
				ISelection selection = viewer.getSelection();
				if (!(selection instanceof IStructuredSelection))
					return false;
				Object[] objs = ((IStructuredSelection)selection).toArray();
				for (int i = 0; i < objs.length; ++i)
					if (objs[i] instanceof ICProject)
						return true;
				return false;
			}
		};
		rebuildAction.setText(CUIPlugin.getResourceString("IndexView.rebuildIndex.name")); //$NON-NLS-1$
		
		openDefinitionAction = new IndexAction() {
			public void run() {
				ISelection selection = viewer.getSelection();
				if (!(selection instanceof IStructuredSelection))
					return;
				
				Object[] objs = ((IStructuredSelection)selection).toArray();
				for (int i = 0; i < objs.length; ++i) {
					if (!(objs[i] instanceof PDOMBinding))
						continue;
					
					try {
						PDOMBinding binding = (PDOMBinding)objs[i];
						PDOMName name = binding.getFirstDefinition();
						if (name == null)
							name = binding.getFirstDeclaration();
						if (name == null)
							continue;
						
						IASTFileLocation location = name.getFileLocation();
						IPath path = new Path(location.getFileName());
						Object input = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(path);
						if (input == null)
							input = new FileStorage(path);

						IEditorPart editor = EditorUtility.openInEditor(input);
						if (editor != null && editor instanceof ITextEditor) {
							((ITextEditor)editor).selectAndReveal(location.getNodeOffset(), location.getNodeLength());
							return;
						}
					} catch (CoreException e) {
						CUIPlugin.getDefault().log(e);
					}
				}
			}
			public boolean valid() {
				ISelection selection = viewer.getSelection();
				if (!(selection instanceof IStructuredSelection))
					return false;
				Object[] objs = ((IStructuredSelection)selection).toArray();
				for (int i = 0; i < objs.length; ++i)
					if (objs[i] instanceof PDOMBinding)
						return true;
				return false;
			}
		};
		openDefinitionAction.setText(CUIPlugin.getResourceString("IndexView.openDefinition.name"));//$NON-NLS-1$
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				IndexView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
	
	private void fillContextMenu(IMenuManager manager) {
		if (rebuildAction.valid())
			manager.add(rebuildAction);
		if (rebuildAction.valid())
			manager.add(openDefinitionAction);
		//manager.add(new Separator());
		//drillDownAdapter.addNavigationActions(manager);
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}
	
	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				openDefinitionAction.run();
			}
		});
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		//fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}
	
	private void fillLocalToolBar(IToolBarManager manager) {
		//drillDownAdapter.addNavigationActions(manager);
	}
	
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	public void handleChange(PDOMDatabase pdom) {
		viewer.getControl().getDisplay().asyncExec(new Runnable() {
			public void run() {
				viewer.refresh();
//				ICModel model = CoreModel.getDefault().getCModel();
//				viewer.setInput(model);
//				try {
//					ICProject[] cprojects = model.getCProjects();
//					int n = 0;
//					for (int i = 0; i < cprojects.length; ++i) {
//						PDOMDatabase pdom = (PDOMDatabase)PDOM.getPDOM(cprojects[i].getProject()); 
//						if (pdom != null)
//							++n;
//					}
//					viewer.setChildCount(model, n);
//				} catch (CModelException e) {
//					CUIPlugin.getDefault().log(e);
//				}
			}
		});
	}
	
}
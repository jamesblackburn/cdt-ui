/*******************************************************************************
 * Copyright (c) 2007 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Anton Leherbauer (Wind River Systems) - initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.internal.ui.editor;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.TextEditorAction;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IProblemBinding;
import org.eclipse.cdt.core.dom.ast.cpp.CPPASTVisitor;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexName;
import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.core.model.IWorkingCopy;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.IWorkingCopyManager;

import org.eclipse.cdt.internal.core.model.ASTCache.ASTRunnable;

import org.eclipse.cdt.internal.ui.util.EditorUtility;

/**
 * Editor action to toggle between source and header files.
 * 
 * @since 4.0
 */
public class ToggleSourceAndHeaderAction extends TextEditorAction {

	private static class Counter {
		public int fCount;
	}

	/**
	 * Compute the partner file for a translation unit.
	 * The partner file is the corresponding source or header file
	 * based on heuristics.
	 *
	 * @since 4.0
	 */
	private static class PartnerFileComputer extends CPPASTVisitor implements ASTRunnable {
		/** 
		 * When this many times the same partner file is hit, 
		 * we are confident enough to take it.
		 */
		private static final int CONFIDENCE_LIMIT = 15;
		/** 
		 * When this many times no match was found in the index, 
		 * we suspect that we won't get a good partner.
		 */
		private static final int SUSPECT_LIMIT = 15;

		private IIndex fIndex;
		private IPath fFilePath;
		private Map fMap;
		/** The confidence level == number of hits */
		private int fConfidence;
		/** Suspect level == number of no index matches */
		private int fSuspect;
		/** The current favorite partner file */
		private IPath fFavoriteLocation;
		
		{
			shouldVisitDeclarators= true;
		}
		public IStatus runOnAST(IASTTranslationUnit ast) {
			fIndex= ast.getIndex();
			fFilePath= Path.fromOSString(ast.getFilePath());
			fMap= new HashMap();
			if (fIndex != null) {
				ast.accept(this);
			}
			return Status.OK_STATUS;
		}

		public IPath getPartnerFileLocation() {
			return fFavoriteLocation;
		}

		/*
		 * @see org.eclipse.cdt.core.dom.ast.ASTVisitor#visit(org.eclipse.cdt.core.dom.ast.IASTDeclarator)
		 */
		public int visit(IASTDeclarator declarator) {
			if (declarator instanceof IASTFunctionDeclarator) {
				IASTName name= declarator.getName();
				if (name != null && declarator.getNestedDeclarator() == null) {
					IBinding binding= name.resolveBinding();
					if (binding != null && !(binding instanceof IProblemBinding)) {
						boolean isDefinition= name.isDefinition();
						final IIndexName[] partnerNames;
						try {
							if (isDefinition) {
								partnerNames= fIndex.findNames(binding, IIndex.FIND_DECLARATIONS);
							} else {
								partnerNames= fIndex.findNames(binding, IIndex.FIND_DEFINITIONS);
							}
							if (partnerNames.length == 0) {
								++fSuspect;
								if (fSuspect == SUSPECT_LIMIT) {
									fFavoriteLocation= null;
									return PROCESS_ABORT;
								}
							}
							for (int i= 0; i < partnerNames.length; i++) {
								IIndexName partnerName= partnerNames[i];
								IASTFileLocation partnerLocation= partnerName.getFileLocation();
								if (partnerLocation != null) {
									IPath partnerFileLocation= Path.fromOSString(partnerLocation.getFileName());
									if (!fFilePath.equals(partnerFileLocation)) {
										addPotentialPartnerFileLocation(partnerFileLocation);
										if (fConfidence == CONFIDENCE_LIMIT) {
											return PROCESS_ABORT;
										}
									}
								}
							}
						} catch (CoreException exc) {
							CUIPlugin.getDefault().log(exc.getStatus());
						}
					}
				}
			}
			return PROCESS_SKIP;
		}

		private void addPotentialPartnerFileLocation(IPath partnerFileLocation) {
			Counter counter= (Counter)fMap.get(partnerFileLocation);
			if (counter == null) {
				counter= new Counter();
				fMap.put(partnerFileLocation, counter);
			}
			++counter.fCount;
			if (counter.fCount > fConfidence) {
				fConfidence= counter.fCount;
				fFavoriteLocation= partnerFileLocation;
			}
		}
	}

	private static ITranslationUnit fgLastPartnerUnit;
	private static ITranslationUnit fgLastSourceUnit;

	/**
	 * Create a toggle source/header action for the given editor.
	 * 
	 * @param bundle  the resource bundle to take the label, tooltip and description from.
	 * @param prefix  the prefix to be prepended to the resource bundle keys
	 * @param editor  the text editor this action is associated with
	 * @see TextEditorAction#TextEditorAction(ResourceBundle, String, ITextEditor)
	 */
	public ToggleSourceAndHeaderAction(ResourceBundle bundle, String prefix, ITextEditor editor) {
		super(bundle, prefix, editor);
	}

	/*
	 * @see org.eclipse.jface.action.Action#run()
	 */
	public void run() {
		IEditorPart editor = getTextEditor();
		if (editor == null) {
			return;
		}
		IEditorInput input= editor.getEditorInput();
		IWorkingCopyManager manager= CUIPlugin.getDefault().getWorkingCopyManager();				
		IWorkingCopy currentUnit= manager.getWorkingCopy(input);

		ITranslationUnit partnerUnit= computePartnerFile(currentUnit);
		if (partnerUnit != null) {
			fgLastSourceUnit= currentUnit.getOriginalElement();
			fgLastPartnerUnit= partnerUnit;
			try {
				EditorUtility.openInEditor(partnerUnit);
			} catch (PartInitException exc) {
				CUIPlugin.getDefault().log(exc.getStatus());
			} catch (CModelException exc) {
				CUIPlugin.getDefault().log(exc.getStatus());
			}
		}
	}

	/**
	 * Compute the corresponding translation unit for the given unit.
	 * 
	 * @param tUnit  the current source/header translation unit
	 * @return the partner translation unit
	 */
	private ITranslationUnit computePartnerFile(ITranslationUnit tUnit) {
		// try shortcut for fast toggling
		if (fgLastPartnerUnit != null) {
			final ITranslationUnit originalUnit;
			if (tUnit instanceof IWorkingCopy) {
				originalUnit= ((IWorkingCopy)tUnit).getOriginalElement();
			} else {
				originalUnit= tUnit;
			}
			if (originalUnit.getTranslationUnit().equals(fgLastPartnerUnit)) {
				if (fgLastSourceUnit.exists()) {
					// toggle back
					return fgLastSourceUnit;
				}
			}
		}

		IProgressMonitor monitor= new NullProgressMonitor();
		PartnerFileComputer computer= new PartnerFileComputer();
		ASTProvider.getASTProvider().runOnAST(tUnit, ASTProvider.WAIT_ACTIVE_ONLY, monitor, computer);
		IPath partnerFileLoation= computer.getPartnerFileLocation();
		if (partnerFileLoation != null) {
			ITranslationUnit partnerUnit= (ITranslationUnit) CoreModel.getDefault().create(partnerFileLoation);
			if (partnerUnit == null) {
				partnerUnit= CoreModel.getDefault().createTranslationUnitFrom(tUnit.getCProject(), partnerFileLoation);
			}
			return partnerUnit;
		}
		// search partnerfile based on filename/extension
		IPath sourceFileLocation= tUnit.getLocation();
		IPath partnerBasePath= sourceFileLocation.removeFileExtension();
		IContentType contentType= getPartnerContentType(tUnit.getContentTypeId());
		if (contentType != null) {
			String[] partnerExtensions;
			partnerExtensions= contentType.getFileSpecs(IContentType.FILE_EXTENSION_SPEC);
			for (int i= 0; i < partnerExtensions.length; i++) {
				String ext= partnerExtensions[i];
				String partnerFileBasename= partnerBasePath.addFileExtension(ext).lastSegment();
				
				IFile partnerFile= null;
				if (tUnit.getResource() != null) {
					partnerFile= findInContainer(tUnit.getResource().getParent(), partnerFileBasename);
				}
				if (partnerFile == null) {
					partnerFile= findInContainer(tUnit.getCProject().getProject(), partnerFileBasename);
				}
				if (partnerFile != null) {
					ITranslationUnit partnerUnit= (ITranslationUnit) CoreModel.getDefault().create(partnerFile);
					if (partnerUnit != null) {
						return partnerUnit;
					}
				}
				// external tanslation unit - try in same directory
				if (tUnit.getResource() == null) {
					partnerFileLoation= partnerBasePath.removeLastSegments(1).append(partnerFileBasename);
					ITranslationUnit partnerUnit= CoreModel.getDefault().createTranslationUnitFrom(tUnit.getCProject(), partnerFileLoation);
					if (partnerUnit != null) {
						return partnerUnit;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Find a file in the given resource container for the given basename.
	 * 
	 * @param container
	 * @param basename
	 * @return a matching {@link IFile} or <code>null</code>, if no matching file was found
	 */
	private IFile findInContainer(IContainer container, final String basename) {
		final IFile[] result= { null };
		IResourceProxyVisitor visitor= new IResourceProxyVisitor() {
			public boolean visit(IResourceProxy proxy) throws CoreException {
				if (result[0] != null) {
					return false;
				}
				if (!proxy.isAccessible()) {
					return false;
				}
				if (proxy.getType() == IResource.FILE && proxy.getName().equals(basename)) {
					result[0]= (IFile)proxy.requestResource();
					return false;
				}
				return true;
			}};
		try {
			container.accept(visitor, 0);
		} catch (CoreException exc) {
			// ignore
		}
		return result[0];
	}

	private IContentType getPartnerContentType(String contentTypeId) {
		IContentTypeManager mgr= Platform.getContentTypeManager();
		if (contentTypeId.equals(CCorePlugin.CONTENT_TYPE_CHEADER)) {
			return mgr.getContentType(CCorePlugin.CONTENT_TYPE_CSOURCE);
		}
		if (contentTypeId.equals(CCorePlugin.CONTENT_TYPE_CSOURCE)) {
			return mgr.getContentType(CCorePlugin.CONTENT_TYPE_CHEADER);
		}
		if (contentTypeId.equals(CCorePlugin.CONTENT_TYPE_CXXHEADER)) {
			return mgr.getContentType(CCorePlugin.CONTENT_TYPE_CXXSOURCE);
		}
		if (contentTypeId.equals(CCorePlugin.CONTENT_TYPE_CXXSOURCE)) {
			return mgr.getContentType(CCorePlugin.CONTENT_TYPE_CXXHEADER);
		}
		return null;
	}
}
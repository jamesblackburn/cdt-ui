/*******************************************************************************
 * Copyright (c) 2006 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Anton Leherbauer (Wind River Systems) - initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.internal.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TypedPosition;
import org.eclipse.swt.widgets.Display;

import org.eclipse.cdt.core.IPositionConverter;
import org.eclipse.cdt.core.dom.ast.IASTNodeLocation;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorElifStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorElseStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorEndifStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfdefStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIfndefStatement;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.ui.CUIPlugin;

import org.eclipse.cdt.internal.ui.LineBackgroundPainter;
import org.eclipse.cdt.internal.ui.text.ICReconcilingListener;

/**
 * TLETODO Document InactiveCodeHighlighting.
 *
 * @since 4.0
 */
public class InactiveCodeHighlighting implements ICReconcilingListener {

	/**
	 * Implementation of <code>IRegion</code> that can be reused
	 * by setting the offset and the length.
	 */
	private static class HighlightPosition extends TypedPosition implements IRegion {
		public HighlightPosition(int offset, int length, String type) {
			super(offset, length, type);
		}
		public HighlightPosition(IRegion region, String type) {
			super(region.getOffset(), region.getLength(), type);
		}
	}


	/** The line background painter */
	private LineBackgroundPainter fLineBackgroundPainter;
	/** The key for inactive code positions in the background painter */
	private String fHighlightKey;
	/** The current translation unit */
	private ITranslationUnit fTranslationUnit;
	/** The background job doing the AST parsing */
	private Job fUpdateJob;
	/** The lock for job manipulation */
	private Object fJobLock = new Object();
	/** The editor this is installed on */
	private CEditor fEditor;
	/** The list of currently highlighted positions */
	private List fInactiveCodePositions= Collections.EMPTY_LIST;

	/**
	 * @param lineBackgroundPainter
	 */
	public InactiveCodeHighlighting(LineBackgroundPainter lineBackgroundPainter, String highlightKey) {
		fLineBackgroundPainter= lineBackgroundPainter;
		fHighlightKey= highlightKey;
	}

	/**
	 * Schedule update of the inactive code positions in the background.
	 */
	private void scheduleJob() {
		synchronized (fJobLock) {
			if (fUpdateJob == null) {
				fUpdateJob = new Job(CEditorMessages.getString("InactiveCodeHighlighting_job")) { //$NON-NLS-1$
					protected IStatus run(IProgressMonitor monitor) {
						IStatus result = Status.OK_STATUS;
						if (fTranslationUnit != null) {
							IASTTranslationUnit ast= CUIPlugin.getDefault().getASTProvider().getAST(fTranslationUnit, ASTProvider.WAIT_YES, monitor);
							reconciled(ast, null, monitor);
						}
						if (monitor.isCanceled()) {
							result = Status.CANCEL_STATUS;
						}
						return result;
					}
				};
				fUpdateJob.setPriority(Job.DECORATE);
			}
			if (fUpdateJob.getState() == Job.NONE) {
				// schedule later if AST is not available yet
				fUpdateJob.schedule();
			}
		}
	}

	/**
	 * @param editor
	 */
	public void install(CEditor editor) {
		assert fEditor == null;
		fEditor= editor;
		fEditor.addReconcileListener(this);
		ICElement cElement= fEditor.getInputCElement();
		if (cElement instanceof ITranslationUnit) {
			fTranslationUnit = (ITranslationUnit)cElement;
		} else {
			fTranslationUnit = null;
		}
	}

	/**
	 * 
	 */
	public void uninstall() {
		if (fLineBackgroundPainter != null) {
			fLineBackgroundPainter.removeHighlightPositions(fInactiveCodePositions);
			fInactiveCodePositions= Collections.EMPTY_LIST;
		}
		if (fEditor != null) {
			fEditor.removeReconcileListener(this);
			fEditor= null;
			fTranslationUnit= null;
		}
	}

	public void dispose() {
		fLineBackgroundPainter= null;
		uninstall();
	}

	/**
	 * Force refresh.
	 */
	public void refresh() {
		scheduleJob();
	}

	/*
	 * @see org.eclipse.cdt.internal.ui.text.ICReconcilingListener#aboutToBeReconciled()
	 */
	public void aboutToBeReconciled() {
	}

	/*
	 * @see org.eclipse.cdt.internal.ui.text.ICReconcilingListener#reconciled(IASTTranslationUnit, IPositionConverter, IProgressMonitor)
	 */
	public void reconciled(IASTTranslationUnit ast, final IPositionConverter positionTracker, IProgressMonitor progressMonitor) {
		if (progressMonitor != null && progressMonitor.isCanceled()) {
			return;
		}
		final List newInactiveCodePositions = collectInactiveCodePositions(ast);
		if (positionTracker != null) {
			for (int i = 0, sz = newInactiveCodePositions.size(); i < sz; i++) {
				IRegion pos = (IRegion) newInactiveCodePositions.get(i);
				newInactiveCodePositions.set(i, new HighlightPosition(positionTracker.historicToActual(pos), fHighlightKey));
			}
		}
		Runnable updater = new Runnable() {
			public void run() {
				if (fEditor != null && fLineBackgroundPainter != null) {
					fLineBackgroundPainter.replaceHighlightPositions(fInactiveCodePositions, newInactiveCodePositions);
					fInactiveCodePositions= newInactiveCodePositions;
				}
			}
		};
		if (fEditor != null) {
			Display.getDefault().asyncExec(updater);
		}
	}

	/**
	 * Collect source positions of preprocessor-hidden branches 
	 * in the given translation unit.
	 * 
	 * @param translationUnit  the {@link IASTTranslationUnit}, may be <code>null</code>
	 * @return a {@link List} of {@link IRegion}s
	 */
	private List collectInactiveCodePositions(IASTTranslationUnit translationUnit) {
		if (translationUnit == null) {
			return Collections.EMPTY_LIST;
		}
		String fileName = translationUnit.getFilePath();
		if (fileName == null) {
			return Collections.EMPTY_LIST;
		}
		List positions = new ArrayList();
		int inactiveCodeStart = -1;
		boolean inInactiveCode = false;
		Stack inactiveCodeStack = new Stack();

		IASTPreprocessorStatement[] preprocStmts = translationUnit.getAllPreprocessorStatements();

		for (int i = 0; i < preprocStmts.length; i++) {
			IASTPreprocessorStatement statement = preprocStmts[i];
			if (!fileName.equals(statement.getContainingFilename())) {
				// preprocessor directive is from a different file
				continue;
			}
			if (statement instanceof IASTPreprocessorIfStatement) {
				IASTPreprocessorIfStatement ifStmt = (IASTPreprocessorIfStatement)statement;
				inactiveCodeStack.push(Boolean.valueOf(inInactiveCode));
				if (!ifStmt.taken()) {
					if (!inInactiveCode) {
						IASTNodeLocation nodeLocation = ifStmt.getNodeLocations()[0];
						inactiveCodeStart = nodeLocation.getNodeOffset();
						inInactiveCode = true;
					}
				}
			} else if (statement instanceof IASTPreprocessorIfdefStatement) {
				IASTPreprocessorIfdefStatement ifdefStmt = (IASTPreprocessorIfdefStatement)statement;
				inactiveCodeStack.push(Boolean.valueOf(inInactiveCode));
				if (!ifdefStmt.taken()) {
					if (!inInactiveCode) {
						IASTNodeLocation nodeLocation = ifdefStmt.getNodeLocations()[0];
						inactiveCodeStart = nodeLocation.getNodeOffset();
						inInactiveCode = true;
					}
				}
			} else if (statement instanceof IASTPreprocessorIfndefStatement) {
				IASTPreprocessorIfndefStatement ifndefStmt = (IASTPreprocessorIfndefStatement)statement;
				inactiveCodeStack.push(Boolean.valueOf(inInactiveCode));
				if (!ifndefStmt.taken()) {
					if (!inInactiveCode) {
						IASTNodeLocation nodeLocation = ifndefStmt.getNodeLocations()[0];
						inactiveCodeStart = nodeLocation.getNodeOffset();
						inInactiveCode = true;
					}
				}
			} else if (statement instanceof IASTPreprocessorElseStatement) {
				IASTPreprocessorElseStatement elseStmt = (IASTPreprocessorElseStatement)statement;
				if (!elseStmt.taken() && !inInactiveCode) {
					IASTNodeLocation nodeLocation = elseStmt.getNodeLocations()[0];
					inactiveCodeStart = nodeLocation.getNodeOffset();
					inInactiveCode = true;
				} else if (elseStmt.taken() && inInactiveCode) {
					IASTNodeLocation nodeLocation = elseStmt.getNodeLocations()[0];
					int inactiveCodeEnd = nodeLocation.getNodeOffset() + nodeLocation.getNodeLength();
					positions.add(new HighlightPosition(inactiveCodeStart, inactiveCodeEnd - inactiveCodeStart, fHighlightKey));
					inInactiveCode = false;
				}
			} else if (statement instanceof IASTPreprocessorElifStatement) {
				IASTPreprocessorElifStatement elifStmt = (IASTPreprocessorElifStatement)statement;
				if (!elifStmt.taken() && !inInactiveCode) {
					IASTNodeLocation nodeLocation = elifStmt.getNodeLocations()[0];
					inactiveCodeStart = nodeLocation.getNodeOffset();
					inInactiveCode = true;
				} else if (elifStmt.taken() && inInactiveCode) {
					IASTNodeLocation nodeLocation = elifStmt.getNodeLocations()[0];
					int inactiveCodeEnd = nodeLocation.getNodeOffset() + nodeLocation.getNodeLength();
					positions.add(new HighlightPosition(inactiveCodeStart, inactiveCodeEnd - inactiveCodeStart, fHighlightKey));
					inInactiveCode = false;
				}
			} else if (statement instanceof IASTPreprocessorEndifStatement) {
				IASTPreprocessorEndifStatement endifStmt = (IASTPreprocessorEndifStatement)statement;
				try {
					boolean wasInInactiveCode = ((Boolean)inactiveCodeStack.pop()).booleanValue();
					if (inInactiveCode && !wasInInactiveCode) {
						IASTNodeLocation nodeLocation = endifStmt.getNodeLocations()[0];
						int inactiveCodeEnd = nodeLocation.getNodeOffset() + nodeLocation.getNodeLength();
						positions.add(new HighlightPosition(inactiveCodeStart, inactiveCodeEnd - inactiveCodeStart, fHighlightKey));
					}
					inInactiveCode = wasInInactiveCode;
				}
		 		catch( EmptyStackException e) {}
			}
		}
		if (inInactiveCode) {
			// handle dangling #if?
		}
		return positions;
	}

}
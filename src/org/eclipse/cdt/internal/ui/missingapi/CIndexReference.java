/*******************************************************************************
 * Copyright (c) 2006 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Markus Schorn - initial API and implementation
 *******************************************************************************/ 

package org.eclipse.cdt.internal.ui.missingapi;

import java.util.Comparator;

import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.model.ITranslationUnit;

public class CIndexReference {
	public static final Comparator COMPARE_OFFSET = new Comparator() {
		public int compare(Object o1, Object o2) {
			CIndexReference r1= (CIndexReference) o1;
			CIndexReference r2= (CIndexReference) o2;
			return r1.fOffset - r2.fOffset;
		}
	};
	private int fOffset;
	private int fLength;
	private ITranslationUnit fTranslationUnit;
	
	public CIndexReference(ITranslationUnit tu, IASTName name) {
		fTranslationUnit= tu;
		fOffset= name.getFileLocation().getNodeOffset();
		fLength= name.getFileLocation().getNodeLength();
	}
	public ITranslationUnit getTranslationUnit() {
		return fTranslationUnit;
	}
	public int getOffset() {
		return fOffset; 
	}
	public long getTimestamp() {
		return 0;
	}
	public int getLength() {
		return fLength;
	}
}
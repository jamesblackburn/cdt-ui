/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 **********************************************************************/
package org.eclipse.cdt.internal.ui.text.contentassist;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.cdt.core.dom.ast.ASTCompletionNode;
import org.eclipse.cdt.core.dom.ast.ASTTypeUtil;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTFunctionStyleMacroParameter;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorFunctionStyleMacroDefinition;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorMacroDefinition;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.ICompositeType;
import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IParameter;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.ITypedef;
import org.eclipse.cdt.core.dom.ast.IVariable;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPField;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPNamespace;
import org.eclipse.cdt.core.model.IWorkingCopy;
import org.eclipse.cdt.core.parser.ast.ASTAccessVisibility;
import org.eclipse.cdt.internal.ui.viewsupport.CElementImageProvider;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.text.contentassist.ICompletionContributor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.graphics.Image;

public class DOMCompletionContributor implements ICompletionContributor {

	public void contributeCompletionProposals(ITextViewer viewer,
											  int offset,
											  IWorkingCopy workingCopy,
											  ASTCompletionNode completionNode,
											  List proposals) {
		if (completionNode != null) {
			IASTName[] names = completionNode.getNames();
			if (names == null || names.length == 0)
				// No names, not much we can do here
				return;
			
			// Find all bindings
			List allBindings = new ArrayList();
			for (int i = 0; i < names.length; ++i) {
				if (names[i].getTranslationUnit() == null)
					// The node isn't properly hooked up, must have backtracked out of this node
					continue;
				IBinding[] bindings = names[i].resolvePrefix();
				if (bindings != null)
					for (int j = 0; j < bindings.length; ++j) {
						IBinding binding = bindings[j];
						if (!allBindings.contains(binding))
							allBindings.add(binding);
					}
			}
			
			Iterator iBinding = allBindings.iterator();
			while (iBinding.hasNext()) {
				IBinding binding = (IBinding)iBinding.next();
				handleBinding(binding, completionNode, offset, viewer, proposals);
			}
			
			// Find all macros if there is a prefix
			String prefix = completionNode.getPrefix();
			if (prefix.length() > 0) {
				IASTPreprocessorMacroDefinition[] macros = completionNode.getTranslationUnit().getMacroDefinitions();
				if (macros != null)
					for (int i = 0; i < macros.length; ++i)
						if (macros[i].getName().toString().startsWith(prefix))
							handleMacro(macros[i], completionNode, offset, viewer, proposals);
			}
			
			// Check for the keywords
			if (prefix.length() > 0)
				for (int i = 0; i < keywords.length; ++i)
					if (keywords[i].startsWith(prefix))
						handleKeyword(keywords[i], completionNode, offset, viewer, proposals);
		}
	}

	private void handleBinding(IBinding binding, ASTCompletionNode completionNode, int offset, ITextViewer viewer, List proposals) {
		if (binding instanceof IFunction)
			handleFunction((IFunction)binding, completionNode, offset, viewer, proposals);
		else
			proposals.add(createProposal(binding.getName(), binding.getName(), getImage(binding), completionNode, offset, viewer));
	}
	
	private void handleFunction(IFunction function, ASTCompletionNode completionNode, int offset, ITextViewer viewer, List proposals) {
		Image image = getImage(CElementImageProvider.getFunctionImageDescriptor());
		
		StringBuffer repStringBuff = new StringBuffer();
		repStringBuff.append(function.getName());
		repStringBuff.append('(');
		
		StringBuffer args = new StringBuffer();
		String returnTypeStr = null;
		try {
			IParameter[] params = function.getParameters();
			if (params != null)
				for (int i = 0; i < params.length; ++i) {
					IType paramType = params[i].getType();
					if (i > 0)
						args.append(',');
					
					args.append(ASTTypeUtil.getType(paramType));
					String paramName = params[i].getName();
					if (paramName != null) {
						args.append(' ');
						args.append(paramName);
					}
				}
			
			IType returnType = function.getType().getReturnType();
			if (returnType != null)
				returnTypeStr = ASTTypeUtil.getType(returnType);
		} catch (DOMException e) {
		}
		String argString = args.toString();
		
		StringBuffer descStringBuff = new StringBuffer(repStringBuff.toString());
		descStringBuff.append(argString);
		descStringBuff.append(')');
		
		if (returnTypeStr != null) {
			descStringBuff.append(' ');
			descStringBuff.append(returnTypeStr);
		}
		
		repStringBuff.append(')');
		String repString = repStringBuff.toString();
		String descString = descStringBuff.toString();
		
		CCompletionProposal proposal = createProposal(repString, descString, image, completionNode, offset, viewer);
		proposal.setCursorPosition(repString.length() - 1);
		
		if (argString.length() > 0) {
			CProposalContextInformation info = new CProposalContextInformation(repString, argString);
			info.setContextInformationPosition(offset);
			proposal.setContextInformation(info);
		}
		
		proposals.add(proposal);
	}
	
	private void handleMacro(IASTPreprocessorMacroDefinition macro, ASTCompletionNode completionNode, int offset, ITextViewer viewer, List proposals) {
		String macroName = macro.getName().toString();
		Image image = getImage(CElementImageProvider.getMacroImageDescriptor());
		
		if (macro instanceof IASTPreprocessorFunctionStyleMacroDefinition) {
			IASTPreprocessorFunctionStyleMacroDefinition functionMacro = (IASTPreprocessorFunctionStyleMacroDefinition)macro;
			
			StringBuffer repStringBuff = new StringBuffer();
			repStringBuff.append(macroName);
			repStringBuff.append('(');
			
			StringBuffer args = new StringBuffer();

			IASTFunctionStyleMacroParameter[] params = functionMacro.getParameters();
			if (params != null)
				for (int i = 0; i < params.length; ++i) {
					if (i > 0)
						args.append(", ");
					args.append(params[i].getParameter());
				}
			String argString = args.toString();
			
			StringBuffer descStringBuff = new StringBuffer(repStringBuff.toString());
			descStringBuff.append(argString);
			descStringBuff.append(')');
			
			repStringBuff.append(')');
			String repString = repStringBuff.toString();
			String descString = descStringBuff.toString();
			
			CCompletionProposal proposal = createProposal(repString, descString, image, completionNode, offset, viewer);
			proposal.setCursorPosition(repString.length() - 1);
			
			if (argString.length() > 0) {
				CProposalContextInformation info = new CProposalContextInformation(repString, argString);
				info.setContextInformationPosition(offset);
				proposal.setContextInformation(info);
			}
			
			proposals.add(proposal);
		} else
			proposals.add(createProposal(macroName, macroName, image, completionNode, offset, viewer));
	}
	
	private void handleKeyword(String keyword, ASTCompletionNode completionNode, int offset, ITextViewer viewer, List proposals) {
		// TODO we should really check the context to make sure
		// it is valid for the keyword to appear here
		Image image = getImage(CElementImageProvider.getKeywordImageDescriptor());
		proposals.add(createProposal(keyword, keyword, image, completionNode, offset, viewer));
	}
	
	private CCompletionProposal createProposal(String repString, String dispString, Image image, ASTCompletionNode completionNode, int offset, ITextViewer viewer) {
		int repLength = completionNode.getLength();
		int repOffset = offset - repLength;
		return new CCompletionProposal(repString, repOffset, repLength, image, dispString, 1, viewer);
	}

	private Image getImage(ImageDescriptor desc) {
		return desc != null ? CUIPlugin.getImageDescriptorRegistry().get(desc) : null;
	}
	
	private Image getImage(IBinding binding) {
		ImageDescriptor imageDescriptor = null;
		
		try {
			if (binding instanceof ITypedef) {
				imageDescriptor = CElementImageProvider.getTypedefImageDescriptor();
			} else if (binding instanceof ICompositeType) {
				if (((ICompositeType)binding).getKey() == ICPPClassType.k_class)
					imageDescriptor = CElementImageProvider.getClassImageDescriptor();
				else if (((ICompositeType)binding).getKey() == ICompositeType.k_struct)
					imageDescriptor = CElementImageProvider.getStructImageDescriptor();
				else if (((ICompositeType)binding).getKey() == ICompositeType.k_union)
					imageDescriptor = CElementImageProvider.getUnionImageDescriptor();
			} else if (binding instanceof IFunction) {
				imageDescriptor = CElementImageProvider.getFunctionImageDescriptor();
			} else if (binding instanceof ICPPField) {
				switch (((ICPPField)binding).getVisibility()) {
				case ICPPField.v_private:
					imageDescriptor = CElementImageProvider.getFieldImageDescriptor(ASTAccessVisibility.PRIVATE);
					break;
				case ICPPField.v_protected:
					imageDescriptor = CElementImageProvider.getFieldImageDescriptor(ASTAccessVisibility.PROTECTED);
					break;
				default:
					imageDescriptor = CElementImageProvider.getFieldImageDescriptor(ASTAccessVisibility.PUBLIC);
					break;
				}
			} else if (binding instanceof IVariable) {
				imageDescriptor = CElementImageProvider.getVariableImageDescriptor();
			} else if (binding instanceof ICPPNamespace) {
				imageDescriptor = CElementImageProvider.getNamespaceImageDescriptor();
			}
		} catch (DOMException e) {
		}
		
		return imageDescriptor != null
			? CUIPlugin.getImageDescriptorRegistry().get( imageDescriptor )
			: null;
	}

	// These are the keywords we complete
	// We only do the ones that are > 5 characters long
	private static String [] keywords = {
		"const_cast",
		"continue",
		"default",
		"delete",
		"double",
		"dynamic_cast",
		"explicit",
		"export",
		"extern",
		"friend",
		"inline",
		"mutable",
		"namespace",
		"operator",
		"private",
		"protected",
		"register",
		"reinterpret_cast",
		"return",
		"signed",
		"sizeof",
		"static",
		"static_cast",
		"struct",
		"switch",
		"template",
		"typedef",
		"typeid",
		"typename",
		"unsigned",
		"virtual",
		"volatile",
		"wchar_t"
	};

}
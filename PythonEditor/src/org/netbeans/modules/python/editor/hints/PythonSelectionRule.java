/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.python.editor.hints;

import javax.swing.text.BadLocationException;
import org.netbeans.modules.gsf.api.Rule.SelectionRule;
import java.util.List;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.lexer.PythonLexerUtils;
import org.netbeans.modules.python.editor.lexer.PythonTokenId;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.gsf.api.Hint;
import org.netbeans.modules.gsf.api.OffsetRange;
import org.netbeans.modules.gsf.api.Rule.UserConfigurableRule;
import org.openide.util.Exceptions;
import org.python.antlr.PythonTree;

/**
 * Represents a rule to be run on text selection
 *
 * @author Tor Norbye
 */
public abstract class PythonSelectionRule implements SelectionRule, UserConfigurableRule {
    protected abstract int getApplicability(PythonRuleContext context, PythonTree root, OffsetRange astRange);

    //public abstract void run(PythonRuleContext context, List<Hint> result);
    public void run(PythonRuleContext context, List<Hint> result) {
        // TODO - decide if this code represents a complete statement...
        // For now - that's true iff there's no code to the left on the
        // start line and code to the right on the end line
        BaseDocument doc = context.doc;
        int originalStart = context.selectionStart;
        int originalEnd = context.selectionEnd;
        int docLength = doc.getLength();

        if (originalEnd > docLength) {
            return;
        }
        OffsetRange narrowed = PythonLexerUtils.narrow(doc, new OffsetRange(originalStart, originalEnd), false);
        if (narrowed == OffsetRange.NONE) {
            return;
        }

        int start = narrowed.getStart();
        int end = narrowed.getEnd();
        try {
            if (start > Utilities.getRowFirstNonWhite(doc, Math.min(docLength, start))) {
                return;
            }
            if (end < Utilities.getRowLastNonWhite(doc, Math.min(docLength, end)) + 1) {
                return;
            }
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }

        PythonTree root = PythonAstUtils.getRoot(context.parserResult);
        if (root == null) {
            return;
        }

        OffsetRange astRange = PythonAstUtils.getAstOffsets(context.compilationInfo, new OffsetRange(start, end));
        if (astRange == OffsetRange.NONE) {
            return;
        }

        int applicability = getApplicability(context, root, astRange);
        if (applicability == 0) {
            return;
        }
        // Don't allow extract with if you're inside strings or comments
        Token<? extends PythonTokenId> startToken = PythonLexerUtils.getToken(doc, start);
        Token<? extends PythonTokenId> endToken = PythonLexerUtils.getToken(doc, end);
        if (startToken == null || endToken == null) {
            return;
        }
        TokenId startId = startToken.id();
        if (startId == PythonTokenId.STRING_LITERAL ||
                (startId == PythonTokenId.COMMENT && start > 0 && startToken == PythonLexerUtils.getToken(doc, start - 1))) {
            return;
        }
        TokenId endId = endToken.id();
        if (endId == PythonTokenId.STRING_LITERAL) {
            return;
        }

        // TODO - don't enable inside comments or strings!!
        // TODO - if you are including functions or classes it should probably
        // be disabled!

        OffsetRange range = new OffsetRange(originalStart, originalEnd);

        run(context, result, range, applicability);
    }

    public abstract void run(PythonRuleContext context, List<Hint> result, OffsetRange range, int applicability);
}

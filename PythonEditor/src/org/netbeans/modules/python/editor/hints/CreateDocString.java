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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.lexer.PythonLexerUtils;
import org.netbeans.modules.python.editor.lexer.PythonTokenId;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.netbeans.modules.gsf.api.CompilationInfo;
import org.netbeans.modules.gsf.api.EditList;
import org.netbeans.modules.gsf.api.Hint;
import org.netbeans.modules.gsf.api.HintFix;
import org.netbeans.modules.gsf.api.HintSeverity;
import org.netbeans.modules.gsf.api.OffsetRange;
import org.netbeans.modules.gsf.api.PreviewableFix;
import org.netbeans.modules.gsf.api.RuleContext;
import org.netbeans.modules.gsf.spi.GsfUtilities;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.ClassDef;
import org.python.antlr.ast.FunctionDef;

/**
 * Offer to create docstrings.
 * @todo Handle modules?
 * @todo Handle parameter tags (for epydoc etc)
 *
 * @author Tor Norbye
 */
public class CreateDocString extends PythonAstRule {
    @Override
    public Set<Class> getKinds() {
        Set<Class> classes = new HashSet<Class>();
        classes.add(FunctionDef.class);
        classes.add(ClassDef.class);

        return classes;
    }

    @Override
    public void run(PythonRuleContext context, List<Hint> result) {

        PythonTree node = context.node;
        if (PythonAstUtils.getDocumentationNode(node) != null) {
            return;
        }

        // Create new fix
        CompilationInfo info = context.compilationInfo;
        OffsetRange astOffsets = PythonAstUtils.getNameRange(info, node);
        OffsetRange lexOffsets = PythonLexerUtils.getLexerOffsets(info, astOffsets);
        BaseDocument doc = context.doc;
        try {
            if (lexOffsets != OffsetRange.NONE && lexOffsets.getStart() < doc.getLength() &&
                    (context.caretOffset == -1 ||
                    Utilities.getRowStart(doc, context.caretOffset) == Utilities.getRowStart(doc, lexOffsets.getStart()))) {
                List<HintFix> fixList = new ArrayList<HintFix>();
                boolean singleIsDefault = node.getClass() == FunctionDef.class;
                fixList.add(new CreateDocStringFix(context, node, !singleIsDefault));
                fixList.add(new CreateDocStringFix(context, node, singleIsDefault));
                String displayName = getDisplayName();
                Hint desc = new Hint(this, displayName, info.getFileObject(), lexOffsets, fixList, 1500);
                result.add(desc);
            }
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public String getId() {
        return "CreateDocString"; // NOI18N
    }

    public String getDisplayName() {
        return NbBundle.getMessage(CreateDocString.class, "CreateDocString");
    }

    public String getDescription() {
        return NbBundle.getMessage(CreateDocString.class, "CreateDocStringDesc");
    }

    public boolean getDefaultEnabled() {
        return true;
    }

    public JComponent getCustomizer(Preferences node) {
        return null;
    }

    public boolean appliesTo(RuleContext context) {
        return true;
    }

    public boolean showInTasklist() {
        return false;
    }

    public HintSeverity getDefaultSeverity() {
        return HintSeverity.CURRENT_LINE_WARNING;
    }

    private static class CreateDocStringFix implements PreviewableFix {
        private final PythonRuleContext context;
        private final PythonTree node;
        private final boolean multiLine;
        private int editListPosition;

        private CreateDocStringFix(PythonRuleContext context, PythonTree node, boolean multiLine) {
            this.context = context;
            this.node = node;
            this.multiLine = multiLine;
        }

        public String getDescription() {
            return multiLine ? NbBundle.getMessage(CreateDocString.class, "CreateDocStringFixMulti") : NbBundle.getMessage(CreateDocString.class, "CreateDocStringFix");
        }

        public boolean canPreview() {
            return true;
        }

        public EditList getEditList() throws Exception {
            BaseDocument doc = context.doc;
            EditList edits = new EditList(doc);

            OffsetRange astRange = PythonAstUtils.getRange(node);
            if (astRange != OffsetRange.NONE) {
                OffsetRange lexRange = PythonLexerUtils.getLexerOffsets(context.compilationInfo, astRange);
                if (lexRange != OffsetRange.NONE) {
                    // Find the colon
                    TokenSequence<? extends PythonTokenId> ts = PythonLexerUtils.getPositionedSequence(doc, lexRange.getStart());
                    if (ts != null) {
                        Token<? extends PythonTokenId> token = PythonLexerUtils.findNextIncluding(ts, Collections.singletonList(PythonTokenId.COLON));
                        if (token != null) {
                            int offset = ts.offset();
                            if (offset < lexRange.getEnd()) {
                                int indent = GsfUtilities.getLineIndent(doc, lexRange.getStart()) +
                                        IndentUtils.indentLevelSize(doc);
                                StringBuilder sb = new StringBuilder();
                                sb.append(IndentUtils.createIndentString(doc, indent));
                                int rowEnd = Utilities.getRowEnd(doc, offset) + 1;
                                sb.append("\"\"\""); // NOI18N
                                if (multiLine) {
                                    sb.append("\n"); // NOI18N
                                    sb.append(IndentUtils.createIndentString(doc, indent));
                                }
                                editListPosition = rowEnd + sb.length();
                                if (multiLine) {
                                    sb.append("\n"); // NOI18N
                                    sb.append(IndentUtils.createIndentString(doc, indent));
                                }
                                sb.append("\"\"\"\n"); // NOI18N
                                edits.replace(rowEnd, 0, sb.toString(), false, 0);
                            }
                        }
                    }
                }
            }

            return edits;
        }

        public void implement() throws Exception {
            EditList edits = getEditList();

            Position pos = edits.createPosition(editListPosition);
            edits.apply();
            if (pos != null && pos.getOffset() != -1) {
                JTextComponent target = GsfUtilities.getPaneFor(context.compilationInfo.getFileObject());
                if (target != null) {
                    target.setCaretPosition(pos.getOffset());
                }
            }
        }

        public boolean isSafe() {
            return true;
        }

        public boolean isInteractive() {
            return false;
        }
    }
}

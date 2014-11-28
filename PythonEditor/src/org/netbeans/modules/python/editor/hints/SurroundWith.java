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
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.lib.editor.codetemplates.api.CodeTemplateManager;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.netbeans.modules.gsf.api.EditList;
import org.netbeans.modules.gsf.api.Hint;
import org.netbeans.modules.gsf.api.HintFix;
import org.netbeans.modules.gsf.api.HintSeverity;
import org.netbeans.modules.gsf.api.OffsetRange;
import org.netbeans.modules.gsf.api.PreviewableFix;
import org.netbeans.modules.gsf.api.RuleContext;
import org.netbeans.modules.gsf.spi.GsfUtilities;
import org.netbeans.modules.python.editor.lexer.PythonLexerUtils;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.python.antlr.PythonTree;
import org.python.antlr.Visitor;
import org.python.antlr.ast.ClassDef;
import org.python.antlr.ast.FunctionDef;
import org.python.antlr.ast.Import;
import org.python.antlr.ast.ImportFrom;

/**
 * Offer to surround code with for example try/except/finally
 *
 * @author Tor Norbye
 */
public class SurroundWith extends PythonSelectionRule {
    @Override
    protected int getApplicability(PythonRuleContext context, PythonTree root, OffsetRange astRange) {
        if (!ApplicabilityVisitor.applies(root, astRange)) {
            return 0;
        }

        return 1;
    }

    @Override
    public void run(PythonRuleContext context, List<Hint> result, OffsetRange range, int applicability) {
        int start = range.getStart();
        int end = range.getEnd();

        // Adjust the fix range to be right around the dot so that the light bulb ends up
        // on the same line as the caret and alt-enter works
        JTextComponent target = GsfUtilities.getPaneFor(context.compilationInfo.getFileObject());
        if (target != null) {
            int dot = target.getCaret().getDot();
            range = new OffsetRange(dot, dot);
        }

        List<HintFix> fixList = new ArrayList<HintFix>(3);
        fixList.add(new SurroundWithFix(context, start, end, false, true));
        fixList.add(new SurroundWithFix(context, start, end, true, true));
        fixList.add(new SurroundWithFix(context, start, end, true, false));
        String displayName = getDisplayName();
        Hint desc = new Hint(this, displayName, context.compilationInfo.getFileObject(),
                range, fixList, 1500);
        result.add(desc);
    }

    public boolean appliesTo(RuleContext context) {
        return true;
    }

    public String getDisplayName() {
        return NbBundle.getMessage(SurroundWith.class, "SurroundWith");
    }

    public boolean showInTasklist() {
        return false;
    }

    public HintSeverity getDefaultSeverity() {
        return HintSeverity.CURRENT_LINE_WARNING;
    }

    public String getId() {
        return "SurroundWith"; // NOI18N
    }

    public String getDescription() {
        return "";
    }

    public boolean getDefaultEnabled() {
        return true;
    }

    public JComponent getCustomizer(Preferences node) {
        return null;
    }

    private static class SurroundWithFix implements PreviewableFix {
        private final PythonRuleContext context;
        private final boolean includeFinally;
        private final boolean includeExcept;
        private Position startPos;
        private Position endPos;
        private Position codeTemplatePos;
        private String codeTemplateText;
        private final int start;
        private final int end;

        private SurroundWithFix(PythonRuleContext context,
                int start, int end,
                boolean includeFinally, boolean includeExcept) {
            assert includeExcept || includeFinally;

            this.context = context;

            OffsetRange range = PythonLexerUtils.narrow(context.doc, new OffsetRange(start, end), false);
            this.start = range.getStart();
            this.end = range.getEnd();
            this.includeFinally = includeFinally;
            this.includeExcept = includeExcept;
        }

        public String getDescription() {
            if (includeExcept && includeFinally) {
                return NbBundle.getMessage(CreateDocString.class, "SurroundWithTEF");
            } else if (includeExcept) {
                return NbBundle.getMessage(CreateDocString.class, "SurroundWithTE");
            } else {
                assert includeFinally;
                return NbBundle.getMessage(CreateDocString.class, "SurroundWithTF");
            }
        }

        public boolean canPreview() {
            return true;
        }

        public EditList getEditList() throws Exception {
            return getEditList(true);
        }

        private EditList getEditList(boolean previewOnly) throws Exception {
            BaseDocument doc = context.doc;
            EditList edits = new EditList(doc);

            int indentSize = IndentUtils.indentLevelSize(doc);
            String oneIndent = IndentUtils.createIndentString(doc, indentSize);
            //int initialIndent = GsfUtilities.getLineIndent(doc, start);
            int lineStart = Utilities.getRowStart(doc, start);
            String initialIndentStr = IndentUtils.createIndentString(doc, IndentUtils.lineIndent(doc, lineStart));
            int nextLine = Utilities.getRowEnd(doc, end) + 1;
            if (nextLine > doc.getLength()) {
                nextLine = doc.getLength();
                edits.replace(nextLine, 0, "\n", false, 1);
            }

            // Indent the selected lines
            edits.replace(lineStart, 0, initialIndentStr + "try:\n", false, 1);
            for (int offset = start; offset < end; offset = Utilities.getRowEnd(doc, offset) + 1) {
                edits.replace(offset, 0, oneIndent, false, 1);
            }

            StringBuilder sb = new StringBuilder();
            if (includeExcept) {
                sb.append(initialIndentStr);
                sb.append("except ");
                if (!previewOnly) {
                    sb.append("${except default=\"");
                }
                sb.append("Exception, e");
                if (!previewOnly) {
                    sb.append("\"}");
                }
                sb.append(":\n");
                sb.append(initialIndentStr);
                sb.append(oneIndent);
                int caretDelta = sb.length();
                startPos = edits.createPosition(nextLine + caretDelta);
                if (!previewOnly) {
                    sb.append("${action default=\"");
                }
                sb.append("print \"Exception: \", e");
                if (!previewOnly) {
                    sb.append("\"}");
                }
                caretDelta = sb.length();
                endPos = edits.createPosition(nextLine + caretDelta);
                sb.append("\n");
                if (!previewOnly && !includeExcept) {
                    sb.append("${cursor}");
                }
            }
            if (includeFinally) {
                sb.append(initialIndentStr);
                sb.append("finally:\n");
                sb.append(initialIndentStr);
                sb.append(oneIndent);
                if (!previewOnly) {
                    sb.append("${finally default=\"\"}\n${cursor}");
                }
                int caretDelta = sb.length();
                if (!includeExcept) {
                    endPos = startPos = edits.createPosition(nextLine + caretDelta);
                }
                sb.append("\n");
            }
            if (previewOnly) {
                edits.replace(nextLine, 0, sb.toString(), false, 1);
            } else {
                codeTemplatePos = edits.createPosition(nextLine, Position.Bias.Backward);
                codeTemplateText = sb.toString();
            }

            return edits;
        }

        public void implement() throws Exception {
            EditList edits = getEditList(true);

            JTextComponent target = GsfUtilities.getPaneFor(context.compilationInfo.getFileObject());
            edits.apply();
            if (target != null) {
                if (codeTemplateText != null && codeTemplatePos != null) {
                    final CodeTemplateManager ctm = CodeTemplateManager.get(context.doc);
                    if (ctm != null) {
                        target.getCaret().setDot(codeTemplatePos.getOffset());
                        ctm.createTemporary(codeTemplateText).insert(target);
                    }
                } else if (startPos != null && endPos != null) {
                    target.setSelectionStart(startPos.getOffset());
                    target.setSelectionEnd(endPos.getOffset());
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

    /** @todo Prune search in traverse, ala AstPath.
     *  @todo Build up start and end AstPaths.
     */
    private static class ApplicabilityVisitor extends Visitor {
        private boolean applies = true;
        private final int start;
        private final int end;

        static boolean applies(PythonTree root, OffsetRange astRange) {
            ApplicabilityVisitor visitor = new ApplicabilityVisitor(astRange);
            try {
                visitor.visit(root);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
                return false;
            }
            return visitor.isApplicable();
        }

        ApplicabilityVisitor(OffsetRange astRange) {
            this.start = astRange.getStart();
            this.end = astRange.getEnd();
        }

        public boolean isApplicable() {
            return applies;
        }

        private void maybeBail(PythonTree node) {
            int nodeStart = node.getCharStartIndex();
            int nodeEnd = node.getCharStopIndex();
            if (nodeStart >= start && nodeStart < end) {
                applies = false;
            }
            if (nodeEnd > start && nodeEnd < end) {
                applies = false;
            }
        }

        @Override
        public void traverse(PythonTree node) throws Exception {
            if (!applies) {
                return;
            }

            int nodeStart = node.getCharStartIndex();
            int nodeStop = node.getCharStopIndex();
            //if (!(nodeStop < start || nodeStart > end)) {
            if (nodeStop >= start && nodeStart <= end) {
                super.traverse(node);
            }
        }

        @Override
        public Object visitClassDef(ClassDef node) throws Exception {
            maybeBail(node);
            return super.visitClassDef(node);
        }

        @Override
        public Object visitFunctionDef(FunctionDef node) throws Exception {
            maybeBail(node);
            return super.visitFunctionDef(node);
        }

        @Override
        public Object visitImport(Import node) throws Exception {
            maybeBail(node);
            return super.visitImport(node);
        }

        @Override
        public Object visitImportFrom(ImportFrom node) throws Exception {
            maybeBail(node);
            return super.visitImportFrom(node);
        }

        // Module is okay - you get this when you select all text in a simple "script" file
        // with only statements
        //@Override
        //public Object visitModule(Module node) throws Exception {
        //    maybeBail(node);
        //    return super.visitModule(node);
        //}
    }
}

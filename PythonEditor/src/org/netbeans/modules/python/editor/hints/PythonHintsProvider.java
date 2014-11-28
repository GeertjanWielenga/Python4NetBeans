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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.netbeans.modules.gsf.api.HintSeverity;
import org.netbeans.modules.python.editor.AstPath;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.gsf.api.CompilationInfo;
import org.netbeans.modules.gsf.api.Error;
import org.netbeans.modules.gsf.api.Hint;
import org.netbeans.modules.gsf.api.HintFix;
import org.netbeans.modules.gsf.api.HintsProvider;
import org.netbeans.modules.gsf.api.OffsetRange;
import org.netbeans.modules.gsf.api.ParserResult;
import org.netbeans.modules.gsf.api.Rule;
import org.netbeans.modules.gsf.api.RuleContext;
import org.netbeans.modules.gsf.spi.GsfUtilities;
import org.netbeans.modules.python.editor.PythonParserResult;
import org.openide.util.Exceptions;
import org.python.antlr.PythonTree;
import org.python.antlr.Visitor;

/**
 *
 * @todo Write rules based on the PythonChecker ideas:
 *   http://pychecker.sourceforge.net/
 * @todo Write rules based on the PyLint ideas:
 *   http://www.logilab.org/projects/pylint
 *   http://www.logilab.org/card/pylintfeatures
 *
 * @author Tor Norbye
 */
public class PythonHintsProvider implements HintsProvider {
    private boolean cancelled;

    public PythonHintsProvider() {
    }

    private static class ScopeRule implements Rule {
        public boolean appliesTo(RuleContext context) {
            return true;
        }

        public String getDisplayName() {
            return "";
        }

        public boolean showInTasklist() {
            return true;
        }

        public HintSeverity getDefaultSeverity() {
            return HintSeverity.ERROR;
        }
    }

    public void computeErrors(HintsManager manager, RuleContext context, List<Hint> result, List<Error> unhandled) {
        ParserResult parserResult = context.parserResult;
        if (parserResult == null) {
            return;
        }

        PythonParserResult pr = (PythonParserResult)parserResult;
        List<Error> scopeErrors = pr.getSymbolTable().getErrors();
        if (scopeErrors.size() > 0) {
            List<HintFix> fixList = Collections.emptyList();
            Rule rule = new ScopeRule(); // HACK! Just need a rule that will return a severity!
            for (Error error : scopeErrors) {
                Hint desc = new Hint(rule, error.getDisplayName(), error.getFile(),
                        new OffsetRange(error.getStartPosition(), error.getEndPosition()), fixList, 10);
                result.add(desc);
            }
        }

        List<Error> errors = parserResult.getDiagnostics();
        if (errors == null || errors.size() == 0) {
            return;
        }
//
//        cancelled = false;
//
//        @SuppressWarnings("unchecked")
//        Map<String,List<JsErrorRule>> hints = (Map)manager.getErrors();
//
//        if (hints.isEmpty() || isCancelled()) {
        unhandled.addAll(errors);
//            return;
//        }
//
//        for (Error error : errors) {
//            if (!applyErrorRules(manager, context, error, hints, result)) {
//                unhandled.add(error);
//            }
//        }
    }

    public void computeSelectionHints(HintsManager manager, RuleContext context, List<Hint> result, int start, int end) {
        cancelled = false;

        if (GsfUtilities.isCodeTemplateEditing(context.doc)) {
            return;
        }

        ParserResult parserResult = context.parserResult;
        if (parserResult == null) {
            return;
        }
        PythonTree root = PythonAstUtils.getRoot(context.parserResult);

        if (root == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<PythonSelectionRule> hints = (List<PythonSelectionRule>)manager.getSelectionHints();

        if (hints.isEmpty()) {
            return;
        }

        if (isCancelled()) {
            return;
        }

        try {
            context.doc.readLock();
            applySelectionRules(manager, context, hints, result);
        } finally {
            context.doc.readUnlock();
        }
}

    private void applySelectionRules(HintsManager manager, RuleContext context, List<PythonSelectionRule> rules, List<Hint> result) {

        PythonRuleContext pythonContext = (PythonRuleContext)context;

        for (PythonSelectionRule rule : rules) {
            if (!rule.appliesTo(context)) {
                continue;
            }

            if (!manager.isEnabled(rule)) {
                continue;
            }

            try {
                context.doc.readLock();
                rule.run(pythonContext, result);
            } finally {
                context.doc.readUnlock();
            }
        }
    }

    public void computeHints(HintsManager manager, RuleContext context, List<Hint> result) {
        cancelled = false;

        if (context.parserResult == null) {
            return;
        }
        PythonTree root = PythonAstUtils.getRoot(context.parserResult);

        if (root == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<Class, List<PythonAstRule>> hints = (Map)manager.getHints(false, context);

        if (hints.isEmpty()) {
            return;
        }

        if (isCancelled()) {
            return;
        }

//        AstPath path = new AstPath();
//        path.descend(root);
//
//        //applyRules(manager, NodeTypes.ROOTNODE, root, path, info, hints, descriptions);
//        applyHints(manager, context, -1, root, path, hints, result);
//
//        scan(manager, context, root, path, hints, result);
//        path.ascend();


        RuleApplicator finder = new RuleApplicator(manager, context, hints, result);
        try {
            context.doc.readLock();
            finder.visit(root);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            context.doc.readUnlock();
        }
    }

    @SuppressWarnings("unchecked")
    public void computeSuggestions(HintsManager manager, RuleContext context, List<Hint> result, int caretOffset) {
        cancelled = false;
        if (context.parserResult == null) {
            return;
        }

        PythonTree root = PythonAstUtils.getRoot(context.parserResult);

        if (root == null) {
            return;
        }

        Map<Class, List<PythonAstRule>> suggestions = new HashMap<Class, List<PythonAstRule>>();
        suggestions.putAll((Map)manager.getHints(true, context));

        Set<Entry<Class, List<PythonAstRule>>> entrySet = (Set)manager.getSuggestions().entrySet();
        for (Entry<Class, List<PythonAstRule>> e : entrySet) {
            List<PythonAstRule> rules = suggestions.get(e.getKey());

            if (rules != null) {
                List<PythonAstRule> res = new LinkedList<PythonAstRule>();

                res.addAll(rules);
                res.addAll(e.getValue());

                suggestions.put(e.getKey(), res);
            } else {
                suggestions.put(e.getKey(), e.getValue());
            }
        }

        if (suggestions.isEmpty()) {
            return;
        }


        if (isCancelled()) {
            return;
        }

        try {
            context.doc.readLock();

            CompilationInfo info = context.compilationInfo;
            int astOffset = PythonAstUtils.getAstOffset(info, caretOffset);
            AstPath path = AstPath.get(root, astOffset);
            Iterator<PythonTree> it = path.leafToRoot();
            while (it.hasNext()) {
                if (isCancelled()) {
                    return;
                }

                PythonTree node = it.next();

                applySuggestions(manager, context, node.getClass(), node, path, suggestions, result);
            }
        } finally {
            context.doc.readUnlock();
        }

    //applyRules(NodeTypes.ROOTNODE, path, info, suggestions, caretOffset, result);
    }

    private void applySuggestions(HintsManager manager, RuleContext context, Class nodeType, PythonTree node, AstPath path, Map<Class, List<PythonAstRule>> hints,
            List<Hint> result) {
        List<PythonAstRule> rules = hints.get(nodeType);

        if (rules != null) {
            PythonRuleContext pyCtx = (PythonRuleContext)context;
            pyCtx.node = node;
            pyCtx.path = path;

            try {
                context.doc.readLock();
                for (PythonAstRule rule : rules) {
                    if (manager.isEnabled(rule)) {
                        rule.run(pyCtx, result);
                    }
                }
            } finally {
                context.doc.readUnlock();
            }
        }
    }

//    /** Apply error rules and return true iff somebody added an error description for it */
//    private boolean applyErrorRules(HintsManager manager, RuleContext context, Error error, Map<String,List<JsErrorRule>> hints,
//            List<Hint> result) {
//        String code = error.getKey();
//        if (code != null) {
//            List<JsErrorRule> rules = hints.get(code);
//
//            if (rules != null) {
//                int countBefore = result.size();
//                PythonRuleContext jsContext = (PythonRuleContext)context;
//
//                boolean disabled = false;
//                for (JsErrorRule rule : rules) {
//                    if (!manager.isEnabled(rule)) {
//                        disabled = true;
//                    } else if (rule.appliesTo(context)) {
//                        rule.run(jsContext, error, result);
//                    }
//                }
//
//                return disabled || countBefore < result.size() || jsContext.remove;
//            }
//        }
//
//        return false;
//    }
//
//    private void applySelectionRules(HintsManager manager, RuleContext context, List<JsSelectionRule> rules,
//            List<Hint> result) {
//
//        for (JsSelectionRule rule : rules) {
//            if (!rule.appliesTo(context)) {
//                continue;
//            }
//
//            //if (!manager.isEnabled(rule)) {
//            //    continue;
//            //}
//
//            rule.run(context, result);
//        }
//    }
//
    public void cancel() {
        cancelled = true;
    }

    private boolean isCancelled() {
        return cancelled;
    }

    public RuleContext createRuleContext() {
        return new PythonRuleContext();
    }

    public List<Rule> getBuiltinRules() {
        return Collections.emptyList();
    }

    private static class RuleApplicator extends Visitor {
        private HintsManager manager;
        private RuleContext context;
        private AstPath path;
        private Map<Class, List<PythonAstRule>> hints;
        private List<Hint> result;

        public RuleApplicator(HintsManager manager, RuleContext context, Map<Class, List<PythonAstRule>> hints, List<Hint> result) {
            this.manager = manager;
            this.context = context;
            this.hints = hints;
            this.result = result;

            path = new AstPath();
        }

        @Override
        public void traverse(PythonTree node) throws Exception {
            path.descend(node);
            applyHints(node);
            super.traverse(node);
            path.ascend();
        }

        private void applyHints(PythonTree node) {
            List<PythonAstRule> rules = hints.get(node.getClass());

            if (rules != null) {
                PythonRuleContext jsContext = (PythonRuleContext)context;
                jsContext.node = node;
                jsContext.path = path;

                for (PythonAstRule rule : rules) {
                    if (manager.isEnabled(rule)) {
                        rule.run(jsContext, result);
                    }
                }
            }
        }
    }
}

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
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.PythonUtils;
import org.netbeans.modules.python.editor.lexer.PythonLexerUtils;
import org.netbeans.modules.gsf.api.CompilationInfo;
import org.netbeans.modules.gsf.api.EditList;
import org.netbeans.modules.gsf.api.OffsetRange;
import org.netbeans.modules.gsf.api.Hint;
import org.netbeans.modules.gsf.api.HintFix;
import org.netbeans.modules.gsf.api.HintSeverity;
import org.netbeans.modules.gsf.api.PreviewableFix;
import org.netbeans.modules.gsf.api.RuleContext;
import org.openide.util.NbBundle;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.ClassDef;
import org.python.antlr.ast.FunctionDef;
import org.python.antlr.ast.Module;
import org.python.antlr.ast.arguments;

import static org.netbeans.modules.python.editor.hints.NameStyle.*;

/**
 * Check names to see if they conform to standard Python conventions.
 * These are documented here:
 *   http://www.python.org/dev/peps/pep-0008/
 * 
 * @todo Add fix to rename!
 * @todo Implement variable name checking!
 * 
 * 
 * @author Tor Norbye
 */
public class NameRule extends PythonAstRule {
    private static final String CLASS_STYLE_NAME = "classStyle"; // NOI18N
    private static final String IGNORED_NAMES = "ignoredNames"; // NOI18N
    private static final String MODULE_STYLE_NAME = "moduleStyle"; // NOI18N
    private static final String FUNCTION_STYLE_NAME = "functionStyle"; // NOI18N
    private static final String SELF_REQUIRED_NAME = "selfRequired"; // NOI18N
    private static final String VARIABLE_STYLE_NAME = "variableStyle"; // NOI18N
    private static NameStyle moduleStyle;
    private static NameStyle functionStyle;
    private static NameStyle classStyle;
    private static NameStyle variableStyle;
    private static String ignoredNames;
    private static boolean selfRequired;

    public NameRule() {
    }

    public boolean appliesTo(RuleContext context) {
        moduleStyle = null; // Ensure lazy init

        return true;
    }

    private static void initializeFromPrefs(PythonRuleContext context, NameRule rule) {
        Preferences pref = context.manager.getPreferences(rule);
        moduleStyle = getModuleNameStyle(pref);
        classStyle = getClassNameStyle(pref);
        functionStyle = getFunctionNameStyle(pref);
        variableStyle = getVariableNameStyle(pref);
        ignoredNames = getIgnoredNames(pref);
        selfRequired = isSelfRequired(pref);
    }

    public Set<Class> getKinds() {
        Set<Class> classes = new HashSet<Class>();
        classes.add(Module.class);
        classes.add(FunctionDef.class);
        classes.add(ClassDef.class);

        return classes;
    }

    public void run(PythonRuleContext context, List<Hint> result) {
        if (moduleStyle == null) {
            initializeFromPrefs(context, this);
        }

        // TODO - check module name!!

        PythonTree node = context.node;
        if (node instanceof Module) {
            if (moduleStyle != NO_PREFERENCE) {
                String moduleName = PythonUtils.getModuleName(context.compilationInfo.getFileObject(), null);
                if (!moduleStyle.complies(moduleName) && !moduleStyle.complies(moduleName.substring(moduleName.lastIndexOf('.') + 1))) {
                    String typeKey = "Module"; // NOI18N
                    String message = NbBundle.getMessage(NameRule.class, "WrongStyle", moduleName,
                            NbBundle.getMessage(NameRule.class, typeKey),
                            moduleStyle.getDisplayName());
                    List<HintFix> hintFixes = getNameStyleFixes(moduleName, context, moduleStyle, MODULE_STYLE_NAME, typeKey);
                    addError(moduleName, context, message, node, result, hintFixes);
                }
            }
        } else if (node instanceof FunctionDef) {
            FunctionDef def = (FunctionDef)node;
            if (functionStyle != NO_PREFERENCE) {
                if (!functionStyle.complies(def.getInternalName())) {
                    String typeKey = "Function"; // NOI18N
                    String message = NbBundle.getMessage(NameRule.class, "WrongStyle", def.getInternalName(),
                            NbBundle.getMessage(NameRule.class, typeKey),
                            functionStyle.getDisplayName());
                    List<HintFix> hintFixes = getNameStyleFixes(def.getInternalName(), context, functionStyle, FUNCTION_STYLE_NAME, typeKey);
                    addError(def.getInternalName(), context, message, def, result, hintFixes);
                }
            }

            // Functions should have a first argument of name "self"
            if (selfRequired && !PythonAstUtils.isStaticMethod(def)) {
                arguments args = def.getInternalArgs();
                if (args.getInternalArgs().size() > 0) {
                    String name = PythonAstUtils.getName(args.getInternalArgs().get(0));
                    if (!("self".equals(name) || "cls".equals(name))) { // NOI18N
                        // Make sure it's a class; other methods don't have to
                        if (PythonAstUtils.isClassMethod(context.path, def)) {
                            String message = NbBundle.getMessage(NameRule.class,
                                    // TODO - determine if it should be cls or def
                                    "NameRuleWrongArg", // NOI18N
                                    name);
                            List<HintFix> fixList = new ArrayList<HintFix>(2);
                            fixList.add(new SelfParamFix(context, true, def, null));
                            List<String> parameters = PythonAstUtils.getParameters(def);
                            if (parameters.size() > 0) {
                                fixList.add(new SelfParamFix(context, false, def, parameters.get(0)));
                            }
                            addError(null, context, message, args, result, fixList);
                        }
                    }
                } else if (PythonAstUtils.isClassMethod(context.path, def)) {
                    String message = NbBundle.getMessage(NameRule.class,
                            // TODO - determine if it should be cls or def
                            "NameRuleWrongNoArg"); // NOI18N
                    List<HintFix> fixList = Collections.<HintFix>singletonList(new SelfParamFix(context, true, def, null));
                    addError(null, context, message, args, result, fixList);
                }
            }
        } else if (node instanceof ClassDef) {
            if (functionStyle != NO_PREFERENCE) {
                ClassDef def = (ClassDef)node;
                if (!classStyle.complies(def.getInternalName())) {
                    String typeKey = "Class"; // NOI18N
                    String message = NbBundle.getMessage(NameRule.class, "WrongStyle", def.getInternalName(),
                            NbBundle.getMessage(NameRule.class, typeKey),
                            classStyle.getDisplayName());
                    List<HintFix> hintFixes = getNameStyleFixes(def.getInternalName(), context, classStyle, CLASS_STYLE_NAME, typeKey);
                    addError(def.getInternalName(), context, message, def, result, hintFixes);
                }
            }
        }
    }

    private List<HintFix> getNameStyleFixes(String name, PythonRuleContext context, NameStyle currentStyle, String key, String type) {
        List<HintFix> fixes = new ArrayList<HintFix>(5);

        fixes.add(new IgnoreWordFix(name, this, context));

        for (NameStyle style : NameStyle.values()) {
            if (style == currentStyle || style == NO_PREFERENCE) {
                continue;
            }

            if (style.complies(name)) {
                ChangeStyleFix cs = new ChangeStyleFix(this, context, style, key, type);
                fixes.add(cs);
            }
        }

        // No preference always last
        fixes.add(new ChangeStyleFix(this, context, NO_PREFERENCE, key, type));

        return fixes;
    }

    private void addError(String name, PythonRuleContext context, String message, PythonTree node, List<Hint> result, List<HintFix> fixList) {
        if (name != null && ignoredNames.length() > 0) {
            for (String ignoredName : ignoredNames.split(",")) { // NOI18N
                ignoredName = ignoredName.trim();
                if (name.equals(ignoredName)) {
                    return;
                }
            }
        }

        CompilationInfo info = context.compilationInfo;
        OffsetRange range;
        if (node instanceof Module) {
            range = new OffsetRange(0, 0);
        } else {
            range = PythonAstUtils.getNameRange(info, node);
        }
        range = PythonLexerUtils.getLexerOffsets(info, range);
        if (range != OffsetRange.NONE) {
            if (fixList == null) {
                fixList = Collections.emptyList();
            }
            Hint desc = new Hint(this, message, info.getFileObject(), range, fixList, 1500);
            result.add(desc);
        }
    }

    public String getId() {
        return "NameRule"; // NOI18N
    }

    public String getDisplayName() {
        return NbBundle.getMessage(NameRule.class, "NameRule");
    }

    public String getDescription() {
        return NbBundle.getMessage(NameRule.class, "NameRuleDesc");
    }

    public boolean getDefaultEnabled() {
        return true;
    }

    public boolean showInTasklist() {
        return true;
    }

    public HintSeverity getDefaultSeverity() {
        return HintSeverity.WARNING;
    }

    public JComponent getCustomizer(Preferences node) {
        moduleStyle = null; // Ensure lazy init after this
        return new NameRulePrefs(this, node);
    }

    static NameStyle getNameStyle(String key, NameStyle deflt, Preferences pref) {
        String value = pref.get(key, deflt.name());

        return NameStyle.valueOf(value);
    }

    static NameStyle getModuleNameStyle(Preferences pref) {
        return getNameStyle(MODULE_STYLE_NAME, NameStyle.NO_PREFERENCE, pref);
    }

    static NameStyle getClassNameStyle(Preferences pref) {
        return getNameStyle(CLASS_STYLE_NAME, NameStyle.CAPITALIZED_WORDS, pref);
    }

    static NameStyle getVariableNameStyle(Preferences pref) {
        return getNameStyle(VARIABLE_STYLE_NAME, NameStyle.LOWERCASE_WITH_UNDERSCORES, pref);
    }

    static NameStyle getFunctionNameStyle(Preferences pref) {
        return getNameStyle(FUNCTION_STYLE_NAME, NameStyle.LOWERCASE_WITH_UNDERSCORES, pref);
    }

    static boolean isSelfRequired(Preferences pref) {
        return pref.getBoolean(SELF_REQUIRED_NAME, true);
    }

    static String getIgnoredNames(Preferences pref) {
        return pref.get(IGNORED_NAMES, "");
    }

    void setModuleNameStyle(Preferences pref, NameStyle style) {
        pref.put(MODULE_STYLE_NAME, style.name());
    }

    void setClassNameStyle(Preferences pref, NameStyle style) {
        pref.put(CLASS_STYLE_NAME, style.name());
    }

    void setFunctionNameStyle(Preferences pref, NameStyle style) {
        pref.put(FUNCTION_STYLE_NAME, style.name());
    }

    void setVariableNameStyle(Preferences pref, NameStyle style) {
        pref.put(VARIABLE_STYLE_NAME, style.name());
    }

    void setIgnoredNames(Preferences pref, String ignoredNames) {
        pref.put(IGNORED_NAMES, ignoredNames);
    }

    void setSelfRequired(Preferences pref, boolean requireSelf) {
        pref.putBoolean(SELF_REQUIRED_NAME, requireSelf);
    }

    private static class IgnoreWordFix implements HintFix {
        private String name;
        private NameRule rule;
        private PythonRuleContext context;

        public IgnoreWordFix(String name, NameRule rule, PythonRuleContext context) {
            this.name = name;
            this.rule = rule;
            this.context = context;
        }

        public String getDescription() {
            return NbBundle.getMessage(NameRule.class, "IgnoreWord", name);
        }

        public void implement() throws Exception {
            Preferences pref = context.manager.getPreferences(rule);
            String ignored = getIgnoredNames(pref);
            if (ignored.length() > 0) {
                ignored = ignored + "," + name; // NOI18N
            } else {
                ignored = name;
            }
            pref.put(IGNORED_NAMES, ignored);

            context.manager.refreshHints(context);
        }

        public boolean isSafe() {
            return true;
        }

        public boolean isInteractive() {
            return true;
        }
    }

    private static class ChangeStyleFix implements HintFix {
        private NameRule rule;
        private PythonRuleContext context;
        private NameStyle style;
        private String key;
        private String typeKey;

        public ChangeStyleFix(NameRule rule, PythonRuleContext context, NameStyle style, String key, String type) {
            this.rule = rule;
            this.context = context;
            this.style = style;
            this.key = key;
            this.typeKey = type;
        }

        public String getDescription() {
            if (style == NO_PREFERENCE) {
                return NbBundle.getMessage(NameRule.class, "ChangeNoStyle", NbBundle.getMessage(NameRule.class, typeKey));
            } else {
                return NbBundle.getMessage(NameRule.class, "ChangeStyle", NbBundle.getMessage(NameRule.class, typeKey), style.getDisplayName());
            }
        }

        public void implement() throws Exception {
            Preferences pref = context.manager.getPreferences(rule);
            pref.put(key, style.name());

            context.manager.refreshHints(context);
        }

        public boolean isSafe() {
            return true;
        }

        public boolean isInteractive() {
            return true;
        }
    }

    /**
     * Fix to insert self argument or rename first argument to self
     */
    private static class SelfParamFix implements PreviewableFix {
        private final PythonRuleContext context;
        private final FunctionDef func;
        private final boolean insert;
        private final String first;

        private SelfParamFix(PythonRuleContext context, boolean insert, FunctionDef func, String first) {
            this.context = context;
            this.insert = insert;
            this.func = func;
            this.first = first;

            assert insert || first != null;
        }

        public String getDescription() {
            if (insert) {
                return NbBundle.getMessage(CreateDocString.class, "InsertSelf");
            } else {
                return NbBundle.getMessage(CreateDocString.class, "RenameSelf", first);
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

            OffsetRange astRange = PythonAstUtils.getNameRange(context.compilationInfo, func);
            OffsetRange lexRange = PythonLexerUtils.getLexerOffsets(context.compilationInfo, astRange);
            if (lexRange == OffsetRange.NONE) {
                return edits;
            }
            int paramStart = lexRange.getEnd();
            if (insert) {
                String missing;
                int lineEnd = Utilities.getRowEnd(doc, paramStart);
                int offset = paramStart;
                if (lineEnd > paramStart) {
                    String line = doc.getText(paramStart, lineEnd - paramStart);
                    int paren = line.indexOf('(');
                    int colon = line.indexOf(':');
                    if (paren != -1) {
                        offset = paramStart + paren + 1;
                        missing = "self"; // NOI18N
                        List<String> parameters = PythonAstUtils.getParameters(func);
                        if (parameters.size() > 0) {
                            missing = "self, "; // NOI18N
                        } else {
                            missing = "self"; // NOI18N
                        }
                    } else if (colon != -1) {
                        offset = paramStart + colon;
                        missing = "(self)"; // NOI18N
                    } else {
                        return edits;
                    }
                } else {
                    missing = "(self)"; // NOI18N
                }
                edits.replace(offset, 0, missing, false, 0);
            } else {
                String text = doc.getText(paramStart, doc.getLength() - paramStart);
                int offset = text.indexOf(first);
                if (offset != -1) {
                    offset += paramStart;
                    edits.replace(offset, first.length(), "self", false, 0); // NOI18N
                }
            }


            return edits;
        }

        public void implement() throws Exception {
            EditList edits = getEditList(true);

            edits.apply();
        }

        public boolean isSafe() {
            return true;
        }

        public boolean isInteractive() {
            return false;
        }
    }
}

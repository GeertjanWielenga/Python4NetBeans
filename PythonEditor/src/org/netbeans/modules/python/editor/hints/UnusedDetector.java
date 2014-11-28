/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
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
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2008 Sun Microsystems, Inc.
 */
package org.netbeans.modules.python.editor.hints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import org.netbeans.modules.gsf.api.CompilationInfo;
import org.netbeans.modules.gsf.api.Hint;
import org.netbeans.modules.gsf.api.HintFix;
import org.netbeans.modules.gsf.api.HintSeverity;
import org.netbeans.modules.gsf.api.OffsetRange;
import org.netbeans.modules.gsf.api.RuleContext;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.PythonParserResult;
import org.netbeans.modules.python.editor.lexer.PythonLexerUtils;
import org.netbeans.modules.python.editor.scopes.SymbolTable;
import org.openide.util.NbBundle;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.Assign;
import org.python.antlr.ast.For;
import org.python.antlr.ast.Module;
import org.python.antlr.ast.Tuple;
import org.python.antlr.base.expr;

/**
 * Detect unused variables
 *
 * @todo Find a more reliable way of detecting return tuples without relying on the
 *  parent reference
 *
 * @author Tor Norbye
 */
public class UnusedDetector extends PythonAstRule {
    /** Default names ignored */
    private static final String DEFAULT_IGNORED_NAMES = "_, dummy";
    private static final String PARAMS_KEY = "params"; // NOI18N
    private static final String SKIP_TUPLE_ASSIGN_KEY = "skipTuples"; // NOI18N
    private static final String IGNORED_KEY = "ignorednames"; // NOI18N

    public UnusedDetector() {
    }

    public boolean appliesTo(RuleContext context) {
        return true;
    }

    public Set<Class> getKinds() {
        return Collections.<Class>singleton(Module.class);
    }

    public void run(PythonRuleContext context, List<Hint> result) {
        CompilationInfo info = context.compilationInfo;
        PythonParserResult pr = PythonAstUtils.getParseResult(info);
        SymbolTable symbolTable = pr.getSymbolTable();

        boolean skipParams = true;
        Preferences pref = context.manager.getPreferences(this);
        if (pref != null) {
            skipParams = getSkipParameters(pref);
        }

        List<PythonTree> unusedNames = symbolTable.getUnused(true, skipParams);
        if (unusedNames.size() == 0) {
            return;
        }

        boolean skipTupleAssigns = true;
        Set<String> ignoreNames = Collections.emptySet();
        if (pref != null) {
            skipParams = getSkipParameters(pref);
            skipTupleAssigns = getSkipTupleAssignments(pref);
            String ignoreNamesStr = getIgnoreNames(pref);
            if (ignoreNamesStr.length() > 0) {
                ignoreNames = new HashSet<String>();
                for (String s : ignoreNamesStr.split(",")) { // NOI18N
                    ignoreNames.add(s.trim());
                }
            }
        }

        for (PythonTree node : unusedNames) {
            if (skipTupleAssigns && isTupleAssignment(node)) {
                continue;
            }
            String name = PythonAstUtils.getName(node);
            if (name == null) {
                name = "";
            }
            if (ignoreNames.contains(name)) {
                continue;
            }
            OffsetRange range = PythonAstUtils.getNameRange(info, node);
            range = PythonLexerUtils.getLexerOffsets(info, range);
            if (range != OffsetRange.NONE) {
                List<HintFix> fixList = new ArrayList<HintFix>(3);
                String message = NbBundle.getMessage(NameRule.class, "UnusedVariable", name);
                Hint desc = new Hint(this, message, info.getFileObject(), range, fixList, 2305);
                result.add(desc);
            }
        }
    }

    private boolean isTupleAssignment(PythonTree node) {
        // This may not work right since the parent pointers often aren't set right;
        // find a more efficient way to do it correctly than a path search for each node
        if (node.getParent() instanceof Tuple) {
            // Allow tuples in tuples
            PythonTree parentParent = node.getParent().getParent();
            while (parentParent instanceof Tuple) {
                parentParent = parentParent.getParent();
                node = node.getParent();
            }
            if (parentParent instanceof Assign) {
                Assign assign = (Assign)parentParent;
                List<expr> targets = assign.getInternalTargets();
                if (targets != null && targets.size() > 0 && targets.get(0) == node.getParent()) {
                    return true;
                }
            }
            if (parentParent instanceof For &&
                    ((For)parentParent).getInternalTarget() == node.getParent()) {
                return true;
            }
        }

        return false;
    }

    public String getId() {
        return "Unused"; // NOI18N
    }

    public String getDisplayName() {
        return NbBundle.getMessage(NameRule.class, "Unused");
    }

    public String getDescription() {
        return NbBundle.getMessage(NameRule.class, "UnusedDesc");
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
        return new UnusedDetectorPrefs(node);
    }

    static boolean getSkipParameters(Preferences prefs) {
        return prefs.getBoolean(PARAMS_KEY, true);
    }

    static void setSkipParameters(Preferences prefs, boolean skipParams) {
        if (skipParams) {
            prefs.remove(PARAMS_KEY);
        } else {
            prefs.putBoolean(PARAMS_KEY, false);
        }
    }

    static boolean getSkipTupleAssignments(Preferences prefs) {
        return prefs.getBoolean(SKIP_TUPLE_ASSIGN_KEY, true);
    }

    static void setSkipTupleAssignments(Preferences prefs, boolean skipTupleAssigns) {
        if (skipTupleAssigns) {
            prefs.remove(SKIP_TUPLE_ASSIGN_KEY);
        } else {
            prefs.putBoolean(SKIP_TUPLE_ASSIGN_KEY, false);
        }
    }

    static String getIgnoreNames(Preferences prefs) {
        return prefs.get(IGNORED_KEY, DEFAULT_IGNORED_NAMES);
    }

    static void setIgnoreNames(Preferences prefs, String ignoredNames) {
        if (ignoredNames.length() == 0) {
            prefs.remove(IGNORED_KEY);
        } else {
            prefs.put(IGNORED_KEY, ignoredNames);
        }
    }
}

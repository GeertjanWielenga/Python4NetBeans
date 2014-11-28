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
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */
package org.netbeans.modules.python.editor.refactoring;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.gsf.api.CancellableTask;
import org.netbeans.modules.gsfpath.api.classpath.ClassPath;
import org.netbeans.modules.python.editor.PythonUtils;
import org.netbeans.napi.gsfret.source.ClasspathInfo;
import org.netbeans.napi.gsfret.source.CompilationController;
import org.netbeans.napi.gsfret.source.ModificationResult;
import org.netbeans.napi.gsfret.source.Source;
import org.netbeans.napi.gsfret.source.WorkingCopy;
import org.netbeans.modules.refactoring.spi.*;
import org.netbeans.modules.refactoring.api.*;
import org.openide.filesystems.FileObject;

/**
 * Plugin implementation based on the one for Java refactoring.
 * 
 * @author Jan Becicka
 * @author Tor Norbye
 */
public abstract class PythonRefactoringPlugin extends ProgressProviderAdapter implements RefactoringPlugin, CancellableTask<CompilationController> {
    protected enum Phase {
        PRECHECK, FASTCHECKPARAMETERS, CHECKPARAMETERS, PREPARE, DEFAULT
    };
    private Phase whatRun = Phase.DEFAULT;
    private Problem problem;
    protected volatile boolean cancelRequest = false;
    private volatile CancellableTask currentTask;

    protected abstract Problem preCheck(CompilationController javac) throws IOException;

    protected abstract Problem checkParameters(CompilationController javac) throws IOException;

    protected abstract Problem fastCheckParameters(CompilationController javac) throws IOException;

    protected abstract Source getPythonSource(Phase p);

    public void cancel() {
    }

    public final void run(CompilationController javac) throws Exception {
        switch (whatRun) {
        case PRECHECK:
            this.problem = preCheck(javac);
            break;
        case CHECKPARAMETERS:
            this.problem = checkParameters(javac);
            break;
        case FASTCHECKPARAMETERS:
            this.problem = fastCheckParameters(javac);
            break;
        default:
            throw new IllegalStateException();
        }
    }

    public Problem preCheck() {
        return run(Phase.PRECHECK);
    }

    public Problem checkParameters() {
        return run(Phase.CHECKPARAMETERS);
    }

    public Problem fastCheckParameters() {
        return run(Phase.FASTCHECKPARAMETERS);
    }

    private Problem run(Phase s) {
        this.whatRun = s;
        this.problem = null;
        Source js = getPythonSource(s);
        if (js == null) {
            return null;
        }
        try {
            js.runUserActionTask(this, true);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return problem;
    }

    public void cancelRequest() {
        cancelRequest = true;
        if (currentTask != null) {
            currentTask.cancel();
        }
    }

    protected ClasspathInfo getClasspathInfo(AbstractRefactoring refactoring) {
        ClasspathInfo cpInfo = refactoring.getContext().lookup(ClasspathInfo.class);
        if (cpInfo == null) {
            Logger.getLogger(getClass().getName()).log(Level.INFO, "Missing scope (ClasspathInfo), using default scope (all open projects)");
            cpInfo = PythonRefUtils.getClasspathInfoFor((FileObject)null);
            if (cpInfo != null) {
                refactoring.getContext().add(cpInfo);
            }
        }
        return cpInfo;
    }

    protected static final Problem createProblem(Problem result, boolean isFatal, String message) {
        Problem problem = new Problem(isFatal, message);
        if (result == null) {
            return problem;
        } else if (isFatal) {
            problem.setNext(result);
            return problem;
        } else {
            //problem.setNext(result.getNext());
            //result.setNext(problem);

            // [TODO] performance
            Problem p = result;
            while (p.getNext() != null) {
                p = p.getNext();
            }
            p.setNext(problem);
            return result;
        }
    }

    private Iterable<? extends List<FileObject>> groupByRoot(Iterable<? extends FileObject> data) {
        Map<FileObject, List<FileObject>> result = new HashMap<FileObject, List<FileObject>>();
        for (FileObject file : data) {
            ClassPath cp = ClassPath.getClassPath(file, ClassPath.SOURCE);
            if (cp != null) {
                FileObject root = cp.findOwnerRoot(file);
                if (root != null) {
                    List<FileObject> subr = result.get(root);
                    if (subr == null) {
                        subr = new LinkedList<FileObject>();
                        result.put(root, subr);
                    }
                    subr.add(file);
                }
            }
        }
        return result.values();
    }

    protected final Collection<ModificationResult> processFiles(Set<FileObject> files, CancellableTask<WorkingCopy> task) {
        currentTask = task;
        Collection<ModificationResult> results = new LinkedList<ModificationResult>();
        try {
            // Process Python files and RHTML files separately - and OTHER files separately
            // TODO - now that I don't need separate RHTML models any more, can
            // I just do a single pass?
            Set<FileObject> pythonFiles = new HashSet<FileObject>(2 * files.size());
            Set<FileObject> rhtmlFiles = new HashSet<FileObject>(2 * files.size());
            for (FileObject file : files) {
                if (PythonUtils.isPythonFile(file)) {
                    pythonFiles.add(file);
                //} else if (PythonUtils.isRhtmlOrYamlFile(file)) {
                //    // Avoid opening HUGE Yaml files - they may be containing primarily data
                //    if (file.getSize() > 512*1024) {
                //        continue;
                //    }
                //    rhtmlFiles.add(file);
                }
            }

            Iterable<? extends List<FileObject>> work = groupByRoot(pythonFiles);
            for (List<FileObject> fos : work) {
                final Source source = Source.create(ClasspathInfo.create(fos.get(0)), fos);
                try {
                    results.add(source.runModificationTask(task));
                } catch (IOException ex) {
                    throw (RuntimeException)new RuntimeException().initCause(ex);
                }
            }
            work = groupByRoot(rhtmlFiles);
            for (List<FileObject> fos : work) {
                final Source source = Source.create(ClasspathInfo.create(fos.get(0)), fos);
                try {
                    results.add(source.runModificationTask(task));
                } catch (IOException ex) {
                    throw (RuntimeException)new RuntimeException().initCause(ex);
                }
            }
        } finally {
            currentTask = null;
        }
        return results;
    }

    protected class TransformTask implements CancellableTask<WorkingCopy> {
        private SearchVisitor visitor;
        private PythonElementCtx treePathHandle;

        public TransformTask(SearchVisitor visitor, PythonElementCtx searchedItem) {
            this.visitor = visitor;
            this.treePathHandle = searchedItem;
        }

        public void cancel() {
        }

        public void run(WorkingCopy compiler) throws IOException {
            visitor.setWorkingCopy(compiler);
            visitor.scan();

            //for (PythonElementCtx tree : visitor.getUsages()) {
            //    ElementGripFactory.getDefault().put(compiler.getFileObject(), tree, compiler);
            //}

            fireProgressListenerStep();
        }
    }
}

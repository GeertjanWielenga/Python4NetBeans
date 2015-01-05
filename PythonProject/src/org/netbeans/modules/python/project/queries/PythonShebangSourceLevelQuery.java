/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.python.project.queries;

import org.netbeans.modules.python.source.queries.SourceLevelQueryImplementation;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Ralph Benjamin Ruijs <ralphbenjamin@netbeans.org>
 */
@ServiceProvider(service = SourceLevelQueryImplementation.class)
public class PythonShebangSourceLevelQuery implements SourceLevelQueryImplementation {

    @Override
    public Result getSourceLevel(FileObject pythonFile) {
        return new ResultImpl(pythonFile);
    }

    private final static class ResultImpl implements Result, FileChangeListener {
        private final ChangeSupport cs = new ChangeSupport(this);

        private final FileObject pythonFile;
        private String sourceLevel = "";

        @SuppressWarnings("LeakingThisInConstructor")
        private ResultImpl(FileObject pythonFile) {
            this.pythonFile = pythonFile;
            this.pythonFile.addFileChangeListener(this);
            this.fileChanged(null);
        }

        @Override
        public void addChangeListener(ChangeListener listener) {
            this.cs.addChangeListener(listener);
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fe) {
        }

        @Override
        public void fileChanged(FileEvent fe) {
            if (pythonFile.isValid()) {
                String shebang = null;
                try (Scanner sc = new Scanner(pythonFile.getInputStream())) {
                    if (sc.hasNextLine()) {
                        shebang = sc.nextLine();
                    }
                } catch (FileNotFoundException ex) {
                    Exceptions.printStackTrace(ex);
                }
                processShebang(shebang);
            }
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
        }

        @Override
        public void fileDeleted(FileEvent fe) {
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
        }

        @Override
        public void fileRenamed(FileRenameEvent fe) {
        }

        @Override
        public String getSourceLevel() {
            return this.sourceLevel;
        }

        @Override
        public void removeChangeListener(ChangeListener listener) {
            this.cs.removeChangeListener(listener);
        }

        private void setSourceLevel(String sourceLevel) {
            this.sourceLevel = sourceLevel;
            cs.fireChange();
        }

        private void processShebang(String shebang) {
            if (shebang != null && shebang.startsWith("#!")) {
                try {
                    Process proc = Runtime.getRuntime().exec(shebang.substring(2) + " --version");
                    String version = null;
                    try(Scanner sc = new Scanner(proc.getInputStream())) {
                        if(sc.hasNextLine()) {
                            version = sc.nextLine();
                        }
                    }
                    proc.destroy();
                    if(version != null && !version.isEmpty() && !version.equals(this.sourceLevel)) {
                        setSourceLevel(version);
                    }
                } catch(IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }
}

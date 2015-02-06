/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.python.api;

import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.util.MutexException;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;
import org.openide.util.io.ReaderInputStream;

/**
 *
 * @author alley
 */
public class PythonPlatformManager implements Serializable{
    private static final String PLATFORM_ID_DEFAULT = "default";
    private static final Logger LOGGER = Logger.getLogger(Util.class.getName());

    // Persistence
    private static final String PLATFORM_PREFIX = "pythonplatform."; // NOI18N
    private static final String PLATFORM_INTEPRETER = ".interpreter"; // NOI18N
    private static final String JAVA_LIB_DIR = "javalib"; // NOI18N
    private static final String PLATFORM_NAME = "name"; // NOI18N
    private static final String SOURCE_LEVEL = "sourcelevel"; // NOI18N
    private static final String INTERPRETER_ARGS = "args"; // NOI18N
    private static final String CONSOLE_PATH = "console"; // NOI18N
    private static final String PYTHON_LIB_DIR = "pythonlib"; // NOI18N

    private HashMap<String, PythonPlatform> platforms;
    private String defaultPlatform;
    private volatile boolean autoDetecting;
    private PythonOptions options = PythonOptions.getInstance();
    /**
     * The PythonPlatformManager is a singleton
     */
    private PythonPlatformManager() {
        load();
    }

    /** Singleton instance variable **/
    private static PythonPlatformManager instance;

    private PythonPlatform getBundledPlatform() {
        PythonPlatform platform = new PythonPlatform(PLATFORM_ID_DEFAULT);

        File jythonInstall = new File("C:\\jython2.5.1"); // NOI18N
//        File jythonInstall = InstalledFileLocator.getDefault().locate("jython-2.5.1", "org.jython", false); // NOI18N
        if (!jythonInstall.exists()) {
            return null;
        }

        String binDir = jythonInstall + File.separator + "bin"; // NOI18N
        String cmd = binDir + File.separator + "jython"; // NOI18N
        if (Utilities.isWindows()) {
            cmd += ".bat"; // NOI18N
        } else {
            ensureExecutable(binDir);
        }

        platform.setInterpreterCommand(cmd);
        platform.setInterpreterConsoleComand(cmd);

        // From running
        // % cd o.jython && ant
        // % ./release/jython-2.5.1/bin/jython ../python.core/release/platform_info.py
        // and then updating properties below
        platform.setName("Jython 2.5.1"); // NOI18N
        String jythonInstallDir = jythonInstall.getPath();
        platform.addPythonPath(new String[] { jythonInstallDir + File.separator + "Lib",
           jythonInstallDir + File.separator + "Lib" + File.separator + "site-packages" });
        List<String> list = discoverJythonClasspath(platform.getInterpreterCommand());
        platform.setJavaPath(list.toArray(new String[list.size()]));
        try {
            //platform.setDirty(false);
            storePlatform(platform);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        return platform;
    }

    /**
     * Get instance of the platform manager create one if never created before
     * @return Python Platform Manager
     */
    public static PythonPlatformManager getInstance(){
        if(instance == null) {
            instance = new PythonPlatformManager();
        }
        return instance;
    }

    /**
     * Load Platform data from xml
     * @return Status of load operation
     */
    public void load(){
        platforms = new HashMap<String, PythonPlatform>();

        Map<String, String> p = PropertyUtils.sequentialPropertyEvaluator(null,
                PropertyUtils.globalPropertyProvider()).getProperties();
        if (p == null) { // #115909
            p = Collections.emptyMap();
        }
        boolean foundDefault = false;
        // Based on similar code in RubyPlatformManager
        for (Map.Entry<String, String> entry : p.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PLATFORM_PREFIX) && key.endsWith(PLATFORM_INTEPRETER)) {
                String id = key.substring(PLATFORM_PREFIX.length(),
                        key.length() - PLATFORM_INTEPRETER.length());
                String idDot = id + '.';
                String libDir = p.get(PLATFORM_PREFIX + idDot + PYTHON_LIB_DIR);
                String javaPath = p.get(PLATFORM_PREFIX + idDot + JAVA_LIB_DIR);
                String name = p.get(PLATFORM_PREFIX + idDot + PLATFORM_NAME);
                String sourceLevel = p.get(PLATFORM_PREFIX + idDot + SOURCE_LEVEL);
                String interpreterArgs = p.get(PLATFORM_PREFIX + idDot + INTERPRETER_ARGS);
                String interpreterConsolePath = p.get(PLATFORM_PREFIX + idDot + CONSOLE_PATH);

                String interpreterPath = entry.getValue();

                PythonPlatform platform = new PythonPlatform(id);

                if (interpreterPath != null && interpreterPath.length() > 0) {
                    platform.setInterpreterCommand(interpreterPath);
                }
                if (interpreterConsolePath != null && interpreterConsolePath.length() > 0) {
                    platform.setInterpreterConsoleComand(interpreterConsolePath);
                }
                if (name != null && name.length() > 0) {
                    platform.setName(name);
                }
                if (sourceLevel != null && !sourceLevel.isEmpty()) {
                    platform.setSourceLevel(sourceLevel);
                }
                if (libDir != null && libDir.length() > 0) {
                    platform.setPythonPath(libDir.split(File.pathSeparator));
                }
                if (interpreterArgs != null && interpreterArgs.length() > 0) {
                    platform.setInterpreterArgs(interpreterArgs);
                }
                if (javaPath != null && javaPath.length() > 0) {
                    platform.setJavaPath(javaPath.split(File.pathSeparator));
                }

                platform.setDirty(false);

                platforms.put(platform.getId(), platform);
                if (id.equals(options.getPythonDefault())) {
                    defaultPlatform = id;
                    foundDefault = true;
                }
            }
        }
        if (!foundDefault) {
            PythonPlatform deflt = getBundledPlatform();
            if (deflt != null) {
                defaultPlatform = deflt.getId();
                platforms.put(defaultPlatform, deflt);
            }

            if (Util.isFirstPlatformTouch()) {
                RequestProcessor.getDefault().post(new Runnable() {
                    public void run() {
                        synchronized(PythonPlatformManager.this) {
                            autoDetect();
                        }
                    }
                });
            }
        }
    }

    /**
     * Save the Platform data back to the xml file
     */
    public void save(){
        for (PythonPlatform platform : platforms.values()) {
            try {
                storePlatform(platform);
            } catch(IOException ex){
                Exceptions.printStackTrace(ex);
            }
        }
    }

    public static void storePlatform(final PythonPlatform platform) throws IOException {
        if (!platform.isDirty()) {
            return;
        }
        try {
            ProjectManager.mutex().writeAccess(new Mutex.ExceptionAction<Void>() {
                public Void run() throws IOException {
                    EditableProperties props = PropertyUtils.getGlobalProperties();
                    clearProperties(platform, props);
                    putPlatformProperties(platform, props);
                    PropertyUtils.putGlobalProperties(props);
                    return null;
                }
            });
        } catch (MutexException e) {
            throw (IOException) e.getException();
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("PythonPlatform stored: " + platform);
        }
    }

    private static void clearProperties(PythonPlatform platfrm, EditableProperties props) {
        String id = PLATFORM_PREFIX + platfrm.getId();
        props.remove(id + PLATFORM_INTEPRETER);
        String idDot = id + '.';

        props.remove(PLATFORM_PREFIX + idDot + PYTHON_LIB_DIR);
        props.remove(PLATFORM_PREFIX + idDot + JAVA_LIB_DIR);
        props.remove(PLATFORM_PREFIX + idDot + PLATFORM_NAME);
        props.remove(PLATFORM_PREFIX + idDot + SOURCE_LEVEL);
        props.remove(PLATFORM_PREFIX + idDot + INTERPRETER_ARGS);
        props.remove(PLATFORM_PREFIX + idDot + CONSOLE_PATH);
    }

    private static String getPathString(List<String> path) {
        StringBuilder sb = new StringBuilder();
        for (String p : path) {
            if (sb.length() > 0) {
                sb.append(File.pathSeparator);
            }
            sb.append(p);
        }
        return sb.toString();
    }
    private static void putPlatformProperties(final PythonPlatform platform, final EditableProperties props) throws FileNotFoundException {
        String id = platform.getId();
        String interpreterKey = PLATFORM_PREFIX + id + PLATFORM_INTEPRETER;
        File interpreter = new File(platform.getInterpreterCommand());
        props.setProperty(interpreterKey, interpreter.getAbsolutePath());
        if (!interpreter.isFile()) {
            //throw new FileNotFoundException(interpreter.getAbsolutePath());
            LOGGER.log(Level.WARNING, "Interpreter isn't file: " + interpreter.getAbsolutePath());
            return;
        }
        String idDot = id + '.';
        if (platform.getName() != null) {
            props.setProperty(PLATFORM_PREFIX + idDot + PLATFORM_NAME, platform.getName());
        }
        if (platform.getSourceLevel() != null) {
            props.setProperty(PLATFORM_PREFIX + idDot + SOURCE_LEVEL, platform.getSourceLevel());
        }
        if (platform.getInterpreterArgs() != null) {
            props.setProperty(PLATFORM_PREFIX + idDot + INTERPRETER_ARGS, platform.getInterpreterArgs());
        }
        if (platform.getPythonPath().size() > 0) {
            props.setProperty(PLATFORM_PREFIX + idDot + PYTHON_LIB_DIR, getPathString(platform.getPythonPath()));
        }
        if (platform.getJavaPath().size() > 0) {
            props.setProperty(PLATFORM_PREFIX + idDot + JAVA_LIB_DIR, getPathString(platform.getJavaPath()));
        }
        if (platform.getInterpreterConsoleComand() != null) {
            props.setProperty(PLATFORM_PREFIX + idDot + CONSOLE_PATH, platform.getInterpreterConsoleComand());
        }
    }

    public String getDefaultPlatform() {
        return defaultPlatform;
    }

    public void setDefaultPlatform(String defaultPlatform) {
        this.defaultPlatform = defaultPlatform;
        options.setPythonDefault(defaultPlatform);
        firePlatformsChanged();
    }

    public void addPlatform(PythonPlatform platform){
        platforms.put(platform.getId(), platform);
        firePlatformsChanged();
    }

    public PythonPlatform getPlatform(String name){
        return platforms.get(name);
    }

    /** @todo Rename to getPlatformNameList? */
    public List<String> getPlatformList(){
        return new ArrayList<String>(platforms.keySet());
    }

    public List<PythonPlatform> getPlatforms(){
        List<PythonPlatform> list = new ArrayList<PythonPlatform>(platforms.values());
        Collections.sort(list);

        return list;
    }

    public void removePlatform(String name){
        final PythonPlatform removed = platforms.get(name);
        if (removed != null) {
            try {
                ProjectManager.mutex().writeAccess(new Mutex.ExceptionAction<Void>() {
                    public Void run() throws IOException {
                        EditableProperties props = PropertyUtils.getGlobalProperties();
                        clearProperties(removed, props);
                        PropertyUtils.putGlobalProperties(props);
                        return null;
                    }
                });
            } catch (MutexException e) {
                //throw (IOException) e.getException();
                Exceptions.printStackTrace(e);
            }
        }

        synchronized(this) {
            platforms.remove(name);
            if (defaultPlatform != null && name.equals(defaultPlatform)) {
                if (platforms.size() > 0) {
                    defaultPlatform = platforms.keySet().iterator().next();
                } else {
                    defaultPlatform = null;
                }
            }
        }

        firePlatformsChanged();
    }

    public PythonPlatform findPlatformProperties(String cmd, String id) throws PythonException{
        PythonPlatform platform = null;
        try{
            PythonExecution pye = new PythonExecution();
            int split = cmd.indexOf(" ");
            pye.setCommand(split > 0 ? cmd.substring(0, split) : cmd);
            pye.setDisplayName("Python Properties");
            File info = InstalledFileLocator.getDefault().locate(
                 "platform_info.py", "org.netbeans.modules.python.core", false);
            pye.setScript(info.getAbsolutePath());
            if(split > 0) { 
                String cmdArgs = cmd.substring(split).trim();
                pye.setCommandArgs(cmdArgs);
            }
            pye.setShowControls(false);
            pye.setShowInput(false);
            pye.setShowWindow(false);
            pye.setShowProgress(false);
            pye.setShowSuspended(false);
            //pye.setWorkingDirectory(info.getAbsolutePath().substring(0, info.getAbsolutePath().lastIndexOf(File.separator)));
            pye.setWorkingDirectory(info.getAbsoluteFile().getParent());
            pye.attachOutputProcessor();
            Future<Integer> result = pye.run();
            Integer value = result.get();
            if(value.intValue() == 0){
                Properties prop = new Properties();
                prop.load(new ReaderInputStream(pye.getOutput()));
                // @@@Jean-Yves fix : assume that cmd is the python command
                // if python_info.py is failing to locate command
                // (this may be the case in jython 2.2.1 )
                String command = prop.getProperty("python.command") ;
                if (command == null) {
                    command = cmd;
                }
                String name = prop.getProperty("platform.name");
                if (id == null) {
                    id = computeId(name);
                }
                platform = new PythonPlatform(id);
                platform.setInterpreterCommand(command);
                platform.setInterpreterConsoleComand(command);
                // @@@Jean-Yves end of fix
                platform.setName(name);
                platform.setSourceLevel(prop.getProperty("platform.sourcelevel"));
                String pathString = prop.getProperty("python.path");
                if(pathString != null)
                    platform.setPythonPath(pathString.split(File.pathSeparator));
                if (new File(cmd).getName().toLowerCase().contains("python")){ // NOI18N
                    platform.setInterpreterArgs("-u"); // NOI18N
                }else{
                    List<String> list = discoverJythonClasspath(platform.getInterpreterCommand());
                    platform.setJavaPath(list.toArray(new String[list.size()]));
                }
                if(platforms.size() == 0){
                    setDefaultPlatform(platform.getId());
                }
                platforms.put(platform.getId(), platform);
            }else{
                throw new PythonException("Could not discover Python properties");
            }
        }catch(PythonException ex){
            Exceptions.printStackTrace(ex);
            throw ex;
        }catch(InterruptedException ex){            
            Exceptions.printStackTrace(ex);
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return platform;
    }

    public synchronized void autoDetect() {
        //assert !SwingUtilities.isEventDispatchThread(); // Slow, don't block the UI
        if (autoDetecting) {
            // Already in progress
            return;
        }

        try {
            autoDetecting = true;
            //findBundledJython();
            platforms.clear();
            PythonPlatform deflt = getBundledPlatform();
            if (deflt != null) {
                defaultPlatform = deflt.getId();
                platforms.put(defaultPlatform, deflt);
            }

            PythonAutoDetector ad = new PythonAutoDetector();

            // TODO - Shouldn't we search the user's $PATH/%Path% instead of the below?
            if (Utilities.isWindows()) {
                ad.traverse(new File("c:/"), false);
                ad.traverse(new File("c:/program files"), false);
            }else{
                ad.traverse(new File("/usr/bin"), false);
            }

            for(String path : ad.getMatches()){
                PythonPlatform platform = findPlatformProperties(path, null);
                if (platform != null) {
                    try {
                        storePlatform(platform);
                    } catch (IOException ioe) {
                        Exceptions.printStackTrace(ioe);
                    }
                }
            }
        } catch (PythonException py) {
            Exceptions.printStackTrace(py);
        } finally {
            autoDetecting = false;
        }
        Util.setFirstPlatformTouch(false);
        firePlatformsChanged();
    }

    // We don't need to introspect the bundled Jython - we know everything about it
    //    private void findBundledJython() throws PythonException {
    //        String cmd = "jython-2.5.1" + File.separator + "bin"; // NOI18N
    //        File info = InstalledFileLocator.getDefault().locate(cmd, "org.jython", false);
    //        cmd = info.getAbsolutePath() + File.separator + "jython";
    //        String cp = PythonPlatform.buildPath(discoverJythonClasspath(cmd));
    //        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
    //            cmd += ".bat";
    //            //createJythonBatFile(cmd, cp);
    //        }
    //        else{
    //            ensureExecutable(cmd.substring(0, cmd.lastIndexOf("bin") + 3));
    //        }
    //        findPlatformProperties(cmd, PLATFORM_ID_DEFAULT);
    //        // TODO - store?
    //    }

    //private void checkJar( ArrayList<String> dest , File parent , String jarName  )
    //{
    //    File jythonjar = new File( parent ,  jarName ) ;
    //    if ( jythonjar.isFile() )
    //      dest.add( jythonjar.getAbsolutePath() );
    //}

    private void scanJars(ArrayList<String> dest, File where) {
        File[] contents = where.listFiles();
        if (contents == null) {
          return;
        }
        for(int i =0; i < contents.length; i++){
            if(contents[i].getAbsolutePath().endsWith("jar")){
                dest.add(contents[i].getAbsolutePath());
            }
        }

    }

    private ArrayList<String> discoverJythonClasspath(String command){
        ArrayList<String> temp = new ArrayList<String>();
        //@@@jean-yves in some case bin is not there(jython 2.2.1 installer)
        // => looking for parent path of command  seemed less dangerous
        // + scan for jar in jython home instead of specifiying "jython.jar"
        // since the jar name may change
        File wk = new File(command) ;
        File parent = wk.getParentFile() ;
        if ( parent.getAbsolutePath().endsWith("bin") )
          parent = parent.getParentFile() ; // just up one
        String path = parent.getAbsolutePath() ;

        // chase for jython.jar or jython-complete.jar in parent path
        scanJars( temp , parent) ;
        //@@@jean-yves end of improvements
        path = path + File.separator + "javalib";
        File libDir = new File(path);
        scanJars( temp , libDir) ;
        return temp;
    }
    public void ensureExecutable(String path) {
        // No excute permissions on Windows. On Unix and Mac, try.
        if (Utilities.isWindows()) {
            return;
        }

        String binDirPath = path;
        if (binDirPath == null) {
            return;
        }

        File binDir = new File(binDirPath);
        if (!binDir.exists()) {
            // No Jython bundled installation?
            return;
        }

        // Ensure that the binaries are installed as expected
        // The following logic is from CLIHandler in core/bootstrap:
        File chmod = new File("/bin/chmod"); // NOI18N

        if (!chmod.isFile()) {
            // Linux uses /bin, Solaris /usr/bin, others hopefully one of those
            chmod = new File("/usr/bin/chmod"); // NOI18N
        }

        if (chmod.isFile()) {
            try {
                List<String> argv = new ArrayList<String>();
                argv.add(chmod.getAbsolutePath());
                argv.add("u+rx"); // NOI18N

                String[] files = binDir.list();

                for (String file : files) {
                    argv.add(file);
                }

                ProcessBuilder pb = new ProcessBuilder(argv);
                pb.directory(binDir);
                Util.adjustProxy(pb);

                Process process = pb.start();

                int chmoded = process.waitFor();

                if (chmoded != 0) {
                    throw new IOException("could not run " + argv + " : Exit value=" + chmoded); // NOI18N
                }
            } catch (Throwable e) {
                // 108252 - no loud complaints
                LOGGER.log(Level.INFO, "Can't chmod+x Jython bits", e);
            }
        }
    }

    // Compute a suitable id default for the given platform name (if any)
    private String computeId(String name) {
        if (name == null) {
            name = "";
        }
        String base = name.replace(' ', '_');

        String id = base;
        for (int i = 0; platforms.get(id) != null; i++) {
            id = base + '_' + i;
        }

        return id;
    }

    /**
     * Change support for notifying of platform changes, using vetoable for
     * making it possible to prevent removing of a used platform.
     */
    private final VetoableChangeSupport changeSupport = new VetoableChangeSupport(PythonPlatformManager.class);

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        changeSupport.addVetoableChangeListener(listener);
    }

    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        changeSupport.removeVetoableChangeListener(listener);
    }

    private void firePlatformsChanged() {
        try {
            changeSupport.fireVetoableChange("platforms", null, null); //NOI18N
        } catch (PropertyVetoException ex) {
            // do nothing, vetoing not implemented yet
        }
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.python.project;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.netbeans.modules.python.project.spi.TestRunner;
import org.netbeans.modules.python.project.ui.actions.CleanCommand;
import org.netbeans.modules.python.project.ui.actions.Command;
import org.netbeans.modules.python.project.ui.actions.CopyCommand;
import org.netbeans.modules.python.project.ui.actions.DebugCommand;
import org.netbeans.modules.python.project.ui.actions.DebugSingleCommand;
import org.netbeans.modules.python.project.ui.actions.DeleteCommand;
import org.netbeans.modules.python.project.ui.actions.MoveCommand;
import org.netbeans.modules.python.project.ui.actions.RenameCommand;
import org.netbeans.modules.python.project.ui.actions.RunCommand;
import org.netbeans.modules.python.project.ui.actions.RunSingleCommand;
import org.netbeans.modules.python.project.ui.actions.BuildCommand;
import org.netbeans.modules.python.project.ui.actions.CleanBuildCommand;

import org.netbeans.spi.project.ActionProvider;
import org.openide.LifecycleManager;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

/**
 *
 * @author alley
 * @author Tomas Zezula
 */
public class PythonActionProvider implements ActionProvider {

    PythonProject project;
    
    private final Map<String,Command> commands;

    public PythonActionProvider(PythonProject project) {
        assert project != null;
        commands = new LinkedHashMap<String, Command>();
        Command[] commandArray = new Command[] {
            new DeleteCommand(project),
            new CopyCommand(project),
            new MoveCommand(project),
            new RenameCommand(project),
            new CleanCommand(project),
            new RunSingleCommand(project, false),
            new RunSingleCommand(project, true), // Run as Test
            new RunCommand(project, false),
            new RunCommand(project, true), // Run project as Test
            new DebugCommand(project) ,
            new DebugSingleCommand(project, false),
            new DebugSingleCommand(project, true), // Debug as Test
            new BuildCommand(project), //Build Egg
            new CleanBuildCommand(project) //Clean and Build Egg
        };
        for (Command command : commandArray) {
            commands.put(command.getCommandId(), command);
        }
    }

    public static TestRunner getTestRunner(TestRunner.TestType testType) {
        Collection<? extends TestRunner> testRunners = Lookup.getDefault().lookupAll(TestRunner.class);
        for (TestRunner each : testRunners) {
            if (each.supports(testType)) {
                return each;
            }
        }
        return null;
    }

    public String[] getSupportedActions() {
        final Set<String> names = commands.keySet();
        return names.toArray(new String[names.size()]);
    }

    public void invokeAction(final String commandName, final Lookup context) throws IllegalArgumentException {
        final Command command = findCommand(commandName);
        assert command != null;
        if (command.saveRequired()) {
            LifecycleManager.getDefault().saveAll();
        }
        if (!command.asyncCallRequired()) {
            command.invokeAction(context);
        } else {
            RequestProcessor.getDefault().post(new Runnable() {
                public void run() {
                    command.invokeAction(context);
                }
            });
        }
    }

    public boolean isActionEnabled(String commandName, Lookup context) throws IllegalArgumentException {
        final Command command = findCommand (commandName);
        assert command != null;
        return command.isActionEnabled(context);
    }
    
    private Command findCommand (final String commandName) {
        assert commandName != null;
        return commands.get(commandName);
    }

}

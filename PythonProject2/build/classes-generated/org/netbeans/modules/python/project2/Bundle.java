package org.netbeans.modules.python.project2;
/** Localizable strings for {@link org.netbeans.modules.python.project2}. */
@javax.annotation.Generated(value="org.netbeans.modules.openide.util.NbBundleProcessor")
class Bundle {
    /**
     * @param parser_error_message parser error message
     * @return <i>The $project_basedir/nb-configuration.xml file cannot be parsed. The information contained in the file will be ignored until fixed. This affects several features in the IDE that will not work properly as a result.<br><br> The parsing exception follows:<br></i>{@code parser_error_message}
     * @see PythonAuxilaryConfig
     */
    static String DESC_Problem_Broken_Config(Object parser_error_message) {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "DESC_Problem_Broken_Config", parser_error_message);
    }
    /**
     * @return <i>The $project_basedir/nb-configuration.xml file contains some elements multiple times. That can happen when concurrent changes get merged by version control for example. The IDE however cannot decide which one to use. So until the problem is resolved manually, the affected configuration will be ignored.</i>
     * @see PythonAuxilaryConfig
     */
    static String DESC_Problem_Broken_Config2() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "DESC_Problem_Broken_Config2");
    }
    /**
     * @return <i>Build Egg</i>
     * @see Python2LogicalView
     */
    static String LBL_BuildAction_Name() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "LBL_BuildAction_Name");
    }
    /**
     * @return <i>Clean and Build Egg</i>
     * @see Python2LogicalView
     */
    static String LBL_CleanBuildAction_Name() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "LBL_CleanBuildAction_Name");
    }
    /**
     * @return <i>Debug</i>
     * @see Python2LogicalView
     */
    static String LBL_DebugAction_Name() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "LBL_DebugAction_Name");
    }
    /**
     * @return <i>Run</i>
     * @see Python2LogicalView
     */
    static String LBL_RunAction_Name() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "LBL_RunAction_Name");
    }
    /**
     * @return <i>Test</i>
     * @see Python2LogicalView
     */
    static String LBL_TestAction_Name() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "LBL_TestAction_Name");
    }
    /**
     * @param Path_to_of_project Path to of project
     * @return <i>Python project in </i>{@code Path_to_of_project}
     * @see Python2LogicalView
     */
    static String PythonLogicalView_ProjectTooltipDescription(Object Path_to_of_project) {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "PythonLogicalView.ProjectTooltipDescription", Path_to_of_project);
    }
    /**
     * @return <i>Broken nb-configuration.xml file.</i>
     * @see PythonAuxilaryConfig
     */
    static String TXT_Problem_Broken_Config() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "TXT_Problem_Broken_Config");
    }
    /**
     * @return <i>Duplicate entries found in nb-configuration.xml file.</i>
     * @see PythonAuxilaryConfig
     */
    static String TXT_Problem_Broken_Config2() {
        return org.openide.util.NbBundle.getMessage(Bundle.class, "TXT_Problem_Broken_Config2");
    }
    private void Bundle() {}
}

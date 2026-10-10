package com.bashworks.ugs.jobinfo;

import com.bashworks.ugs.jobinfo.machine.UgsJobInfoMachineGateway;
import com.bashworks.ugs.jobinfo.ui.JobInfoPanel;
import com.willwinder.ugs.nbp.lib.Mode;
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.ugs.nbp.lib.services.LocalizingService;
import com.willwinder.universalgcodesender.model.BackendAPI;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;

import java.awt.BorderLayout;

/**
 * NetBeans TopComponent window entrypoint for UGS Better Job Info.
 * Automatically docked into UGS Platform window layout.
 */
@TopComponent.Description(
        preferredID = "JobInfoTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(
        mode = Mode.EDITOR_SECONDARY,
        openAtStartup = true,
        position = 510)
@ActionID(
        category = LocalizingService.CATEGORY_WINDOW,
        id = "com.bashworks.ugs.jobinfo.JobInfoTopComponent")
@ActionReference(path = LocalizingService.MENU_WINDOW_PLUGIN)
@TopComponent.OpenActionRegistration(
        displayName = "UGS Better Job Info",
        preferredID = "JobInfoTopComponent")
public final class JobInfoTopComponent extends TopComponent {

    private final BackendAPI backend;
    private final UgsJobInfoMachineGateway gateway;
    private final JobInfoPanel panel;

    public JobInfoTopComponent() {
        this.backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        this.gateway = new UgsJobInfoMachineGateway(backend);
        this.panel = new JobInfoPanel(gateway);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);

        refreshTitle();
        setToolTipText("High-accuracy CNC job duration, progress percentage, and time remaining");
    }

    @Override
    protected void componentOpened() {
        refreshTitle();
        backend.addUGSEventListener(gateway);
    }

    @Override
    protected void componentShowing() {
        refreshTitle();
    }

    private void refreshTitle() {
        setName(PluginInfo.TITLE);
        setDisplayName(PluginInfo.TITLE);
        setHtmlDisplayName(null);
    }

    @Override
    protected void componentClosed() {
        backend.removeUGSEventListener(gateway);
        panel.close();
    }
}

package de.culture4life.luca;

import androidx.test.core.app.ApplicationProvider;

import net.lachlanmckee.timberjunit.TimberTestRule;

import org.junit.Rule;

public class LucaUnitTest {

    @Rule
    public TimberTestRule timberTestRule = TimberTestRule.logAllAlways();

    protected LucaApplication application;

    public LucaUnitTest() {
        this.application = ApplicationProvider.getApplicationContext();
    }

    protected <ManagerType extends Manager> ManagerType getInitializedManager(ManagerType manager) {
        initializeManager(manager);
        return manager;
    }

    protected <ManagerType extends Manager> void initializeManager(ManagerType manager) {
        manager.initialize(application).blockingAwait();
    }

    protected <ManagerType extends Manager> void initializeManagers(ManagerType... managers) {
        for (ManagerType manager : managers) {
            initializeManager(manager);
        }
    }

}

package de.culture4life.luca;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import de.culture4life.luca.testtools.rules.FixNestedSpiesMemoryLeakRule;
import de.culture4life.luca.testtools.rules.LoggingRule;
import de.culture4life.luca.testtools.rules.MemoryUsageRule;
import de.culture4life.luca.testtools.rules.ReplaceRxJavaSchedulersRule;

@RunWith(AndroidJUnit4.class)
public abstract class LucaUnitTest {

    public ReplaceRxJavaSchedulersRule.TestSchedulersRule rxSchedulersRule = ReplaceRxJavaSchedulersRule.Companion.manualExecution();

    @Rule
    public RuleChain rules = RuleChain.emptyRuleChain()
            .around(new MemoryUsageRule())
            .around(new FixNestedSpiesMemoryLeakRule())
            .around(new LoggingRule())
            .around(new InstantTaskExecutorRule())
            .around(rxSchedulersRule);

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

    protected void advanceScheduler(long delayTime, TimeUnit unit) {
        rxSchedulersRule.getTestScheduler().advanceTimeBy(delayTime, unit);
    }

    protected void triggerScheduler() {
        rxSchedulersRule.getTestScheduler().triggerActions();
    }

}

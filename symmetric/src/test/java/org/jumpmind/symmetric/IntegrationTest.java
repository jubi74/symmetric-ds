package org.jumpmind.symmetric;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.TestConstants;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class IntegrationTest {

    SymmetricEngine rootEngine;

    SymmetricEngine clientEngine;

    JdbcTemplate rootJdbcTemplate;

    JdbcTemplate clientJdbcTemplate;

    // TODO: move this data access somewhere else
    String insertOrderHeaderSql = "insert into test_order_header (order_id, customer_id, status, deliver_date) values(?,?,?,?)";

    String updateOrderHeaderStatusSql = "update test_order_header set status = ? where order_id = ?";

    String selectOrderHeaderSql = "select order_id, customer_id, status, deliver_date from test_order_header where order_id = ?";

    String insertOrderDetailSql = "insert into test_order_detail (order_id, line_number, item_type, item_id, quantity, price) values(?,?,?,?,?,?)";

    String insertCustomerSql = "insert into test_customer (customer_id, name, is_active, address, city, state, zip, entry_time) values(?,?,?,?,?,?,?,?)";

    @BeforeTest(groups = "integration")
    public void init() {
        SymmetricEngineTestFactory.resetSchemasAndEngines();

        // This will start() the root engine.
        rootEngine = SymmetricEngineTestFactory
                .getMySqlTestEngine1(TestConstants.TEST_ROOT_DOMAIN_SETUP_SCRIPT);
        BeanFactory rootBeanFactory = rootEngine.getApplicationContext();
        rootJdbcTemplate = new JdbcTemplate((DataSource) rootBeanFactory
                .getBean(Constants.DATA_SOURCE));
        rootEngine.openRegistration(TestConstants.TEST_CLIENT_NODE_GROUP,
                TestConstants.TEST_CLIENT_EXTERNAL_ID);

        clientEngine = SymmetricEngineTestFactory.getMySqlTestEngine2(null);
        BeanFactory clientBeanFactory = clientEngine.getApplicationContext();
        clientJdbcTemplate = new JdbcTemplate((DataSource) clientBeanFactory
                .getBean(Constants.DATA_SOURCE));
        Assert.assertTrue(clientEngine.isRegistered(),
                "The client did not register.");
    }

    @Test(groups = "integration")
    public void testSyncToClient() {
        // test pulling no data
        clientEngine.pull();

        // now change some data that should be sync'd
        rootJdbcTemplate.update(insertCustomerSql, new Object[] { 101,
                "Charlie Brown", "1", "300 Grub Street", "New Yorl", "NY",
                90009, new Date() });
        clientEngine.pull();
        Assert
                .assertEquals(
                        clientJdbcTemplate
                                .queryForInt("select count(*) from test_customer where customer_id=101"),
                        1, "The customer was not sync'd to the client.");
    }

    @Test(groups = "integration")
    public void testRejectedRegistration() {

    }

    @Test(groups = "integration", dependsOnMethods = "testSyncToClient")
    public void testSyncToRoot() {
        clientJdbcTemplate.update(insertOrderHeaderSql, new Object[] { "10",
                "100", null, "2007-01-03" });
        clientJdbcTemplate.update(insertOrderDetailSql, new Object[] { "10",
                "1", "STK", "110000065", "3", "3.33" });
        clientEngine.push();
    }

    @Test(groups = "integration")
    public void testSyncInsertCondition() {
        // Should not sync when status = null
        rootJdbcTemplate.update(insertOrderHeaderSql, new Object[] { "11",
                "100", null, "2007-01-02" });
        clientEngine.pull();

        IOutgoingBatchService outgoingBatchService = (IOutgoingBatchService) rootEngine
                .getApplicationContext().getBean(
                        Constants.OUTGOING_BATCH_SERVICE);
        List<OutgoingBatch> batches = outgoingBatchService
                .getOutgoingBatches(TestConstants.TEST_CLIENT_EXTERNAL_ID);
        Assert.assertEquals(batches.size(), 0,
                "There should be no outgoing batches, yet I found some.");

        Assert.assertEquals(clientJdbcTemplate.queryForList(
                selectOrderHeaderSql, new Object[] { "11" }).size(), 0,
                "The order record was sync'd when it should not have been.");

        // Should sync when status = C
        rootJdbcTemplate.update(insertOrderHeaderSql, new Object[] { "12",
                "100", "C", "2007-01-02" });
        clientEngine.pull();
        Assert.assertEquals(clientJdbcTemplate.queryForList(
                selectOrderHeaderSql, new Object[] { "12" }).size(), 1,
                "The order record was not sync'd when it should have been.");
        // TODO: make sure event did not fire
    }

    @SuppressWarnings("unchecked")
    @Test(groups = "integration")
    public void testSyncUpdateCondition() {
        rootJdbcTemplate.update(updateOrderHeaderStatusSql, new Object[] {
                null, "1" });
        clientEngine.pull();
        Assert.assertEquals(clientJdbcTemplate.queryForList(
                selectOrderHeaderSql, new Object[] { "1" }).size(), 0,
                "The order record was sync'd when it should not have been.");

        rootJdbcTemplate.update(updateOrderHeaderStatusSql, new Object[] { "C",
                "1" });
        clientEngine.pull();
        List list = clientJdbcTemplate.queryForList(selectOrderHeaderSql,
                new Object[] { "1" });
        Assert.assertEquals(list.size(), 1, "The order record should exist.");
        Map map = (Map) list.get(0);
        Assert
                .assertEquals(map.get("status"), "C",
                        "Status should be complete");
        // TODO: make sure event did not fire
    }

    @Test(groups = "integration", dependsOnMethods = {
            "testSyncUpdateCondition", "testSyncInsertCondition",
            "testSyncToRoot", "testSyncToClient" })
    public void testPurge() throws Exception {
        Thread.sleep(1000);
        rootEngine.purge();
        clientEngine.purge();
        Assert.assertEquals(rootJdbcTemplate
                .queryForInt("select count(*) from "
                        + TestConstants.TEST_PREFIX + "data"), 0,
                "Expected all data rows to have been purged.");
        Assert.assertEquals(clientJdbcTemplate
                .queryForInt("select count(*) from "
                        + TestConstants.TEST_PREFIX + "data"), 0,
                "Expected all data rows to have been purged.");
        
    }

    @Test(groups = "integration")
    public void testMultipleChannels() {
    }

    @Test(groups = "integration")
    public void testChannelInError() {
    }

    @Test(groups = "integration")
    public void testTableSyncConfigChangeForRoot() {
    }

    @Test(groups = "integration")
    public void testTableSyncConfigChangeForClient() {
    }

    @Test(groups = "integration")
    public void testDataChangeTableChangeDataChangeThenSync() {
    }

    @Test(groups = "integration")
    public void testTransactionalCommit() {
    }

    @Test(groups = "integration")
    public void testTransactionalCommitPastBatchBoundary() {
    }

    @Test(groups = "integration")
    public void testSyncingGlobalParametersFromRoot() {

    }

    @AfterClass(groups = "integration")
    public void tearDown() {
        SymmetricEngineTestFactory.resetSchemasAndEngines();
    }
}

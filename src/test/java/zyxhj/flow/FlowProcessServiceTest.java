//package zyxhj.flow;
//
//import java.util.List;
//
//import org.junit.AfterClass;
//import org.junit.BeforeClass;
//
//import com.alibaba.druid.pool.DruidPooledConnection;
//import com.alibaba.fastjson.JSONArray;
//import com.alibaba.fastjson.JSONObject;
//
//import zyxhj.flow.domain.ProcessDefinition;
//import zyxhj.flow.service.FlowService;
//import zyxhj.utils.Singleton;
//import zyxhj.utils.data.DataSource;
//
//public class FlowProcessServiceTest {
//
//	private static DruidPooledConnection conn;
//
//	private static FlowService flowService;
//
//	@BeforeClass
//	public static void setUpBeforeClass() throws Exception {
//		try {
//			conn = DataSource.getDruidDataSource("rdsDefault.prop").getConnection();
//
//			flowService = Singleton.ins(FlowService.class);
//
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//	@AfterClass
//	public static void tearDownAfterClass() throws Exception {
//		conn.close();
//	}
//
//	private static final Long pdId = 400195073090059L;
//
//	private static final Long activityId = 400196423643694L;
//
//	private static final Long processId = 400159724862966L;
//
//	private static final Long recordId = 400159724862966L;
//
//	public void testAddProcessDefinition() {
//
//
//		JSONArray tags = new JSONArray();
//		tags.add("测试1");
//		tags.add("测试2");
//		JSONArray lanes = new JSONArray();
//		lanes.add("测试泳道名称1");
//		lanes.add("测试泳道名称2");
//		
//		try {
//			flowService.createProcessDefinition(conn, new Long("123456L"), "testTitle", tags, lanes);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//	public void testQueryProcessDefinition() {
//		try {
//			List<ProcessDefinition> jo = flowService.queryProcessDefinition(conn, "testModule", null, 10, 0);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//	public void testAddPDActivity() {
//
//		JSONArray receivers = new JSONArray();
//
//		JSONArray actions = new JSONArray();
//
//		try {
//			flowService.addPDActivity(conn, pdId, "testActivityTitle", "part1", receivers, actions);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//	public void testEditPDActivity() {
//		JSONArray receivers = new JSONArray();
//
//		JSONArray assets = new JSONArray();
//
//		JSONArray actions = new JSONArray();
//
//		JSONObject visualization = new JSONObject();
//
//		try {
//			flowService.editPDActivity(conn, pdId, activityId, "testActivity", "part2", receivers, assets, actions,
//					visualization);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//	public void testDelPDActivity() {
//		try {
//			flowService.delPDActivity(conn, pdId, activityId);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//}
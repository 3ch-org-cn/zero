package zyxhj.utils.data;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import com.alibaba.fastjson.JSON;

import zyxhj.utils.api.ServerException;

public class EXPTest {

	@Test
	public void testParams() {

		// 参数测试，注意顺序

		EXP e1;
		try {
			e1 = EXP.ins().exp("t1", "=", "?", 1).and("t2", "=", "?", 2).and("t5", "LIKE", "%abc%");

			EXP e2 = EXP.ins().exp("tt1", "<>", "?", "a").or("tt2", "=", "?", "b");

			EXP e3 = EXP.ins().exp("t3", "=", "?", 3).and("t4", "=", "?", 4);

			EXP exp = EXP.ins().exp(e1, "AND", e3).and(e2);

			StringBuffer sb = new StringBuffer("WHERE ");
			ArrayList<Object> params = new ArrayList<>();
			exp.toSQL(sb, params);

			String str = sb.toString();
			String pstr = JSON.toJSONString(params);
			System.out.println("===" + str);
			System.out.println(">>>" + pstr);

			// 断言是否正确
			Assert.assertEquals(pstr, "[1,2,3,4,\"a\",\"b\"]");

		} catch (ServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void testExact() {
		// 参数严格性测试
		// true时，参数为空照传
		// false时，参数为空则去除表达式

		Object obj = null;

		try {
			EXP e1 = EXP.ins().exp("t1", "=", "?", 1).and("t2", "=", "?", obj).or("t3", "=", "?", obj);
		} catch (ServerException e) {
			// e.printStackTrace();
			Assert.assertTrue(true);
		}

		try {
			EXP e2 = EXP.ins(false).exp("t1", "=", "?", obj).and("t2", "=", "?", "t2").or("t3", "=", "?", obj);

			StringBuffer sb = new StringBuffer();
			ArrayList<Object> params = new ArrayList<>();

			sb = new StringBuffer();
			params = new ArrayList<>();
			e2.toSQL(sb, params);
			System.out.println(">>>" + sb.toString());

			Assert.assertEquals(sb.toString(), "t2 = ?");
		} catch (ServerException e) {
			Assert.assertTrue(true);
		}

	}

	@Test
	public void testLike() {

		try {
			EXP e = EXP.ins().exp("t1", "=", "?", 1).and(EXP.like("name", "namestr"));

			StringBuffer sb = new StringBuffer();
			ArrayList params = new ArrayList<>();

			e.toSQL(sb, params);
			System.out.println(">>>" + sb.toString());
			Assert.assertEquals(sb.toString(), "t1 = ? AND name LIKE '%namestr%'");
		} catch (ServerException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	@Test
	public void testIn() {
		try {
			EXP e = EXP.ins().exp("t1", "=", "?", 1).and(EXP.in("name", 123, 234, "sdf", 3534, 3453, 334));

			StringBuffer sb = new StringBuffer();
			ArrayList params = new ArrayList<>();

			e.toSQL(sb, params);
			System.out.println(">>>" + sb.toString());
			Assert.assertEquals(sb.toString(), "t1 = ? AND name IN(?,?,?,?,?,?)");
		} catch (ServerException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	@Test
	public void testInOrdered() {
		try {
			EXP e = EXP.ins().exp("t1", "=", "?", 1).and(EXP.inOrdered("name", 123, 234, "sdf", 3534, 3453, 334));

			StringBuffer sb = new StringBuffer();
			ArrayList params = new ArrayList<>();

			e.toSQL(sb, params);
			System.out.println(">>>" + sb.toString());
			Assert.assertEquals(sb.toString(),
					"t1 = ? AND name IN(?,?,?,?,?,?) ORDER BY FIND_IN_SET(name,123,234,sdf,3534,3453,334)");
		} catch (ServerException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	@Test
	public void testRemove() {

		try {
			EXP tt = EXP.ins().exp("this is shit", null).or("this is fuck", null);

			EXP exp = EXP.ins().exp("t1", "=", "?", 1).and("t2", "=", "?", 2).and(tt);

			EXP exp2 = EXP.ins().exp("t1", "=", "?", 1).and("t2", "=", "?", 2).and(tt);

			EXP expMax = EXP.ins().exp(exp).and(exp2);

			StringBuffer sb = new StringBuffer();
			ArrayList<Object> params = new ArrayList<>();
			expMax.toSQL(sb, params);

			String str = sb.toString();
			String pstr = JSON.toJSONString(params);
			System.out.println("===" + str);
			System.out.println(">>>" + pstr);

			Assert.assertEquals(sb.toString(),
					"t1 = ? AND t2 = ? AND (this is shit OR this is fuck) AND (t1 = ? AND t2 = ? AND (this is shit OR this is fuck))");
		} catch (ServerException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	@Test
	public void testANDKey() {
		try {
			EXP e = EXP.key("id", 123).andKey("name", "小四");

			StringBuffer sb = new StringBuffer();
			ArrayList<Object> params = new ArrayList<>();
			e.toSQL(sb, params);

			String str = sb.toString();
			String pstr = JSON.toJSONString(params);
			System.out.println("===" + str);
			System.out.println(">>>" + pstr);

			Assert.assertEquals(sb.toString(), "id = ? AND name = ?");
		} catch (ServerException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
}

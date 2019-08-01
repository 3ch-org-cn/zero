package zyxhj.utils.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alicloud.openservices.tablestore.model.search.query.BoolQuery;
import com.alicloud.openservices.tablestore.model.search.query.Query;
import com.alicloud.openservices.tablestore.model.search.query.RangeQuery;
import com.alicloud.openservices.tablestore.model.search.query.TermQuery;

import zyxhj.utils.api.BaseRC;
import zyxhj.utils.api.ServerException;
import zyxhj.utils.data.ts.ColumnBuilder;
import zyxhj.utils.data.ts.TSObjectMapper;

/**
 * 查询表达式工具</br>
 * 支持 && , || , AND , OR , and , or 连接词</br>
 * 支持 == , = , != , <> , > , < , >= , <= , LIKE 关键字</br>
 * 支持 IN 语句（直接用函数表达式）</br>
 * 不支持BETWEEN</br>
 * 
 * 表达式中的变量用{{}}包裹</br>
 */
public class EXP implements Cloneable {

	private static final String TYPE_EXP = "e";// 二元表达式
	private static final String TYPE_METHOD = "m";// 函数
	// private static final String TYPE_LINK = "-";// 连接表达式

	// 是否严谨，如果为true则表达式不允许为空，如果为false则字段为空时自动跳过
	private boolean exact;
	private String t;
	private String op;
	private List<Object> ps;

	private Object[] args;

	private EXP(boolean exact) {
		this.exact = exact;
	}

	public static EXP ins(boolean exact) {
		return new EXP(exact);
	}

	/**
	 * 默认是严谨的
	 */
	public static EXP ins() {
		return new EXP(true);
	}

	/**
	 * 设置严谨性
	 */
	public EXP exact(boolean exact) {
		this.exact = exact;
		return this;
	}

	/**
	 * SQL的 LIKE 语句</br>
	 */
	public static EXP like(String key, String str) {
		// LIKE语句中，不支持?号参数
		return new EXP(false).exp(StringUtils.join(key, " LIKE '%", str, "%'"), null);
	}

	public static EXP in(String key, Object... args) {
		StringBuffer sb = new StringBuffer(key);
		sb.append(" IN(");
		for (Object arg : args) {
			sb.append("?,");
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(")");

		return new EXP(false).exp(sb.toString(), Arrays.asList(args));
	}

	/**
	 * SQL的 IN 语句（带排序）</br>
	 */
	public static EXP inOrdered(String key, Object... args) {
		StringBuffer sb = new StringBuffer(key);
		sb.append(" IN(");
		for (Object arg : args) {
			sb.append("?,");
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(") ORDER BY FIND_IN_SET(").append(key).append(",");

		for (int i = 0; i < args.length; i++) {
			Object o = args[i];
			if (i < args.length - 1) {
				sb.append(o).append(',');
			} else {
				sb.append(o);
			}
		}
		sb.append(')');

		return new EXP(false).exp(sb.toString(), Arrays.asList(args));
	}

	/**
	 * SQL的 key = value 语句（太常用，所以做一个）</br>
	 * 
	 * @throws ServerException
	 */
	public static EXP key(String key, Object value) throws ServerException {
		return new EXP(true).exp(key, "=", "?", value);
	}

	/**
	 * 拷贝构造
	 * 
	 * @param exp
	 *            需要拷贝的表达式
	 */
	public EXP exp(EXP exp) {
		EXP c = exp.clone();

		this.op = c.op;
		this.ps = c.ps;
		this.t = c.t;
		this.args = c.args;

		return this;
	}

	/**
	 * 函数方法构造
	 * 
	 * @param exact
	 *            是否严谨，true则字段参数不允许为空，false则字段为空时自动跳过
	 * @param str
	 *            表达式文本
	 * @param args
	 *            表达式参数列表，
	 * @return
	 */
	public EXP exp(String str, List<Object> args) {
		this.t = TYPE_METHOD;
		this.op = str;
		this.args = args == null ? null : args.toArray();

		return this;
	}

	public EXP exp(String op, List<Object> ps, Object... args) {
		this.t = TYPE_METHOD;
		this.op = op;
		this.ps = ps;
		this.args = args;

		return this;
	}

	public EXP exp(Object left, String op, Object right, Object... args) throws ServerException {
		if (isShit(exact, right, args)) {
			// 宽松验证参数，且当前右参为空，放弃本次添加
			return this;
		} else {
			this.t = TYPE_EXP;
			this.op = op;
			this.ps = Arrays.asList(left, right);
			this.args = args;

			return this;
		}
	}

	protected EXP clone() {
		EXP clone = null;
		try {
			clone = (EXP) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
		return clone;
	}

	public EXP and(EXP exp) throws ServerException {
		if (StringUtils.isBlank(this.op)) {
			// empty 相当于创建
			exp(exp);
			return this;
		} else {
			if (exp == null) {
				return this;
			} else {
				EXP left = this.clone();
				EXP right = exp;

				this.t = TYPE_EXP;
				this.op = "&&";
				this.ps = Arrays.asList(left, right);
				this.args = null;

				return this;
			}
		}
	}

	public EXP and(String str, List<Object> args) throws ServerException {
		if (StringUtils.isBlank(this.op)) {
			// empty 相当于创建
			exp(this.exact, str, args);
			return this;
		} else {
			if (StringUtils.isBlank(str)) {
				return this;
			} else {
				return and(new EXP(this.exact).exp(str, args));
			}
		}
	}

	public EXP and(String op, List<Object> ps, Object... args) throws ServerException {
		if (StringUtils.isBlank(this.op)) {
			// empty 相当于创建
			exp(op, ps, args);
			return this;
		} else {
			if (StringUtils.isBlank(op) || ps == null) {
				return this;
			} else {
				return and(new EXP(this.exact).exp(op, ps, args));
			}
		}
	}

	public EXP and(Object l, String op, Object r, Object... args) throws ServerException {
		if (isShit(exact, r, args)) {
			// 宽松验证参数，且当前右参为空，放弃本次添加
			return this;
		} else {
			if (StringUtils.isBlank(this.op)) {
				// empty 相当于创建
				exp(l, op, r, args);
				return this;
			} else {
				if (l == null || r == null || StringUtils.isBlank(op)) {
					return this;
				} else {
					return and(new EXP(this.exact).exp(l, op, r, args));
				}
			}
		}
	}

	public EXP andKey(String key, Object value) throws ServerException {
		return and(key, "=", "?", value);
	}

	public EXP or(EXP exp) throws ServerException {
		if (StringUtils.isBlank(this.op)) {
			// empty 相当于创建
			exp(exp);
			return this;
		} else {
			if (exp == null) {
				return this;
			} else {
				EXP left = this.clone();
				EXP right = exp;

				this.t = TYPE_EXP;
				this.op = "||";
				this.ps = Arrays.asList(left, right);
				this.args = null;

				return this;
			}
		}
	}

	public EXP or(String str, List<Object> args) throws ServerException {
		if (StringUtils.isBlank(this.op)) {
			// empty 相当于创建
			exp(str, args);
			return this;
		} else {
			if (StringUtils.isBlank(str)) {
				return this;
			} else {
				return or(new EXP(exact).exp(str, args));
			}
		}
	}

	public EXP or(String op, List<Object> ps, Object... args) throws ServerException {
		if (StringUtils.isBlank(this.op)) {
			// empty 相当于创建
			exp(op, ps, args);
			return this;
		} else {
			if (StringUtils.isBlank(op) || ps == null) {
				return this;
			} else {
				return or(new EXP(this.exact).exp(op, ps, args));
			}
		}
	}

	public EXP or(Object l, String op, Object r, Object... args) throws ServerException {
		if (isShit(exact, r, args)) {
			// 宽松验证参数，且当前右参为空，放弃本次添加
			return this;
		} else {
			if (StringUtils.isBlank(this.op)) {
				// empty 相当于创建
				exp(l, op, r, args);
				return this;
			} else {
				if (l == null || r == null || StringUtils.isBlank(op)) {
					return this;
				} else {
					return or(new EXP(this.exact).exp(l, op, r, args));
				}
			}
		}
	}

	private static String sqlFixOP(String op) {
		op = StringUtils.trim(op);
		if (op.equals("&&") || op.equals("AND") || op.equals("and")) {
			return " AND ";
		} else if (op.equals("||") || op.equals("OR") || op.equals("or")) {
			return " OR ";
		} else if (op.equals("!=") || op.equals("<>")) {
			return " <> ";
		} else if (op.equals("==") || op.equals("=")) {
			return " = ";
		} else {
			return StringUtils.join(" ", op, " ");
		}
	}

	private static String tsFixOP(String op) {
		op = StringUtils.trim(op);
		if (op.equals("&&") || op.equals("AND") || op.equals("and")) {
			return "AND";
		} else if (op.equals("||") || op.equals("OR") || op.equals("or")) {
			return "OR";
		} else if (op.equals("!=") || op.equals("<>")) {
			return "<>";
		} else if (op.equals("==") || op.equals("=")) {
			return "=";
		} else {
			return op;
		}
	}

	private Query makeQuery(String op, List<Object> ps, TSObjectMapper mapper) throws Exception {
		String newop = tsFixOP(op);

		if (newop.equals("=")) {
			// 精确匹配TermQuery
			String fieldName = (String) ps.get(0);
			fieldName = replaceJavaField2RDSField(fieldName, mapper);
			Object value = ps.get(1);

			TermQuery q = new TermQuery();
			q.setFieldName(fieldName);
			q.setTerm(ColumnBuilder.buildColumnValue(value));
			return q;
		} else if (newop.equals("<")) {
			// 范围查询RangeQuery
			String fieldName = (String) ps.get(0);
			fieldName = replaceJavaField2RDSField(fieldName, mapper);
			Object value = ps.get(1);

			RangeQuery q = new RangeQuery(); // 设置查询类型为RangeQuery
			q.setFieldName(fieldName); // 设置针对哪个字段
			q.lessThan(ColumnBuilder.buildColumnValue(value)); // 结束位置值
			return q;
		} else if (newop.equals(">")) {
			// 范围查询RangeQuery
			String fieldName = (String) ps.get(0);
			fieldName = replaceJavaField2RDSField(fieldName, mapper);
			Object value = ps.get(1);

			RangeQuery q = new RangeQuery(); // 设置查询类型为RangeQuery
			q.setFieldName(fieldName); // 设置针对哪个字段
			q.greaterThan(ColumnBuilder.buildColumnValue(value)); // 结束位置值
			return q;
		} else if (newop.equals("<=")) {
			// 范围查询RangeQuery
			String fieldName = (String) ps.get(0);
			fieldName = replaceJavaField2RDSField(fieldName, mapper);
			Object value = ps.get(1);

			RangeQuery q = new RangeQuery(); // 设置查询类型为RangeQuery

			q.setFieldName(fieldName); // 设置针对哪个字段
			q.lessThanOrEqual(ColumnBuilder.buildColumnValue(value)); // 结束位置值
			return q;
		} else if (newop.equals(">=")) {
			// 范围查询RangeQuery
			String fieldName = (String) ps.get(0);
			fieldName = replaceJavaField2RDSField(fieldName, mapper);
			Object value = ps.get(1);

			RangeQuery q = new RangeQuery(); // 设置查询类型为RangeQuery
			q.setFieldName(fieldName); // 设置针对哪个字段
			q.greaterThanOrEqual(ColumnBuilder.buildColumnValue(value)); // 结束位置值
			return q;
		} else if (newop.equals("<>")) {
			// 精确查询TermQuery，然后非查询BoolQuery.setMustNotQueries
			String fieldName = (String) ps.get(0);
			fieldName = replaceJavaField2RDSField(fieldName, mapper);
			Object value = ps.get(1);

			TermQuery q = new TermQuery();
			q.setFieldName(fieldName);
			q.setTerm(ColumnBuilder.buildColumnValue(value));

			BoolQuery bq = new BoolQuery();
			bq.setMustNotQueries(Arrays.asList(q));

			return bq;
		} else {
			throw new ServerException(BaseRC.REPOSITORY_SIMPLE_QUERY_TS_QUERY_UNSPORTED_ERROR, op);
		}
	}

	/**
	 * 修复表达式参数中的java变量{{}}
	 */
	private static String fixJavaFieldInPS(String ps) {
		String ret = ps.replace("{{", "");
		ret = ret.replace("}}", "");
		return ret;
	}

	private static String replaceJavaField2RDSField(String where, TSObjectMapper mapper) throws Exception {
		StringBuffer sb = new StringBuffer();
		int ind = 0;
		int start = 0;
		int end = 0;
		ArrayList<String> ret = new ArrayList<>();
		while (true) {
			start = where.indexOf("{{", ind);
			if (start < ind) {
				// 没有找到新的{，结束
				sb.append(where.substring(ind));
				break;
			} else {
				// 找到{，开始找配对的}
				end = where.indexOf("}}", start);
				if (end > start + 3) {
					// 找到结束符号
					sb.append(where.substring(ind, start));

					ind = end + 2;// 记录下次位置
					String javaField = where.substring(start + 2, end);
					String alias = mapper.getAliasByJavaFieldName(javaField);
					if (StringUtils.isBlank(alias)) {
						throw new ServerException(BaseRC.REPOSITORY_SIMPLE_QUERY_FIELD_ERROR, javaField);
					} else {
						sb.append(alias);
					}
				} else {
					// 没有找到匹配的结束符号，终止循环
					sb.append(where.substring(ind));
					break;
				}
			}
		}
		return sb.toString();
	}

	/**
	 * 
	 */
	public Query toTSQuery(TSObjectMapper mapper) throws Exception {
		if (t.equals(TYPE_EXP)) {
			// 二元表达式
			Object left = ps.get(0);
			Object right = ps.get(1);
			String newop = tsFixOP(op);
			if (left instanceof EXP || right instanceof EXP) {
				Query lquery = ((EXP) left).toTSQuery(mapper);
				Query rquery = ((EXP) right).toTSQuery(mapper);

				BoolQuery boolQuery = new BoolQuery();
				if (newop.equals("AND")) {
					boolQuery.setMustQueries(Arrays.asList(lquery, rquery));
					return boolQuery;
				} else if (newop.equals("OR")) {
					boolQuery.setMinimumShouldMatch(1);// 至少满足一个条件
					boolQuery.setShouldQueries(Arrays.asList(lquery, rquery));
					return boolQuery;
				} else {
					throw new ServerException(BaseRC.REPOSITORY_SIMPLE_QUERY_TS_QUERY_UNSPORTED_ERROR, op);
				}
			} else {
				// left和right都不是表达式，开始分析并构建当前表达式的TSQuery
				return makeQuery(newop, ps, mapper);
			}
		} else {
			// 函数或其它
		}
		return null;
	}

	private static boolean isShit(boolean exact, Object right, Object[] args) throws ServerException {
		if (exact) {
			// 严谨表达式，参数不能为空，为空抛异常
			if ("?".equals(right.toString().trim()) && args != null && args.length > 0 && args[0] == null) {
				throw new ServerException(BaseRC.REPOSITORY_EXP_PARAM_NULL);
			} else {
				return false;
			}
		} else {
			// 宽松表达式，参数为空则放弃当前语句
			if ("?".equals(right.toString().trim()) && args != null && args.length > 0 && args[0] == null) {
				return true;
			} else {
				return false;
			}
		}
	}

	public static void main(String[] args) {

		try {

			EXP tt = new EXP(true);
			tt.exp("this is shit", null).or("this is fuck", null);

			EXP exp = new EXP(true);
			exp.exp("t1", "=", "?", 1).and("t2", "=", "?", 2).and(tt);

			EXP exp2 = new EXP(true);
			exp2.exp("t1", "=", "?", 1).and("t2", "=", "?", 2).and(tt);

			EXP expMax = new EXP(true);
			expMax.exp(exp).and(exp2);

			StringBuffer sb = new StringBuffer("WHERE ");
			ArrayList<Object> params = new ArrayList<>();
			expMax.toSQL(sb, params);

			String str = sb.toString();
			String pstr = JSON.toJSONString(params);
			System.out.println("===" + str);
			System.out.println(">>>" + pstr);

		} catch (ServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static boolean isEndEXP(EXP src) {
		if (src.t.equals(TYPE_METHOD)) {
			return true;
		} else {
			Object left = src.ps.get(0);
			Object right = src.ps.get(1);
			if (left instanceof EXP || right instanceof EXP) {
				// 任意一个节点是表达式，则该表达式不是最终节点
				return false;
			} else {
				return true;
			}
		}
	}

	public void toSQL(StringBuffer sb, List<Object> params) throws ServerException {
		if (t.equals(TYPE_EXP)) {
			// 二元表达式
			Object left = ps.get(0);
			Object right = ps.get(1);

			if (left instanceof EXP) {
				((EXP) left).toSQL(sb, params);
			} else {
				sb.append(left);
			}

			sb.append(sqlFixOP(op));
			if (right instanceof EXP) {
				EXP er = (EXP) right;
				boolean isEnd = EXP.isEndEXP(er);// 下一个节点是否最终节点
				if (!isEnd) {
					sb.append("(");
				}
				er.toSQL(sb, params);
				if (!isEnd) {
					sb.append(")");
				}
			} else {

				if (right instanceof String) {
					if (StringUtils.trim((String) right).equals("?")) {
						// 单个问号的字符不加单引号
						sb.append(right);
					} else {
						sb.append("'").append(right).append("'");
					}
				} else {
					sb.append(right);
				}
			}

			// 添加到参数列表
			if (args != null && args.length > 0) {
				for (Object a : args) {
					params.add(a);
				}
			}

		} else {
			// 函数或其它
			sb.append(op);
			if (ps != null && ps.size() > 0) {
				sb.append('(');
				for (int i = 0; i < ps.size(); i++) {
					Object p = ps.get(i);
					sb.append(p);
					if (i < ps.size() - 1) {
						sb.append(',');
					}
				}
				sb.append(')');
			}
			// 添加到参数列表
			if (args != null && args.length > 0) {
				for (Object a : args) {
					params.add(a);
				}
			}
		}
	}

	/**
	 * JSONObject格式的EXP表达式的解析，用于接口调用
	 */
	public static void jsonEXP2VirtualTableSQL(JSONObject exp, String jsonColumn, StringBuffer sb) throws Exception {
		String op = exp.getString("op");
		String t = exp.getString("t");
		List<Object> ps = exp.getJSONArray("ps");

		if (t.equals(TYPE_EXP)) {
			// 表达式
			Object left = ps.get(0);
			Object right = ps.get(1);
			boolean isLink = false;
			if (op.equals("&&") || op.equals("||")) {
				// 是连接符，代表最终节点，不加括号
				isLink = true;
			}

			if (isLink) {
				sb.append('(');
			}
			if (left instanceof JSONObject) {
				// 递归
				jsonEXP2VirtualTableSQL((JSONObject) left, jsonColumn, sb);
			} else {
				// JSON虚拟表中的字段名，无需进行java转换，直接取出{{}}即可
				String temp = StringUtils.trim(left.toString());
				String ll = fixJavaFieldInPS(temp);
				if (temp.equals(ll)) {
					// 没有java变量
					sb.append(ll);
				} else {
					// 有java变量
					// data->'$.c_data.COL5' = 2
					sb.append(jsonColumn).append("->'$.c_data.").append(ll).append("'");
				}
			}
			sb.append(sqlFixOP(op));
			if (right instanceof JSONObject) {
				jsonEXP2VirtualTableSQL((JSONObject) right, jsonColumn, sb);
			} else {
				// JSON虚拟表中的字段名，无需进行java转换，直接取出{{}}即可
				String temp = StringUtils.trim(right.toString());
				String rr = fixJavaFieldInPS(temp);
				if (temp.equals(rr)) {
					// 没有java变量
					sb.append(rr);
				} else {
					// 有java变量
					// data->'$.c_data.COL5' = 2
					sb.append(jsonColumn).append("->'$.c_data.").append(rr).append("'");
				}
			}
			if (isLink) {
				sb.append(')');
			}
		} else {
			// 方法
			sb.append(op).append('(');
			for (int i = 0; i < ps.size(); i++) {
				sb.append(ps.get(i));
				if (i < ps.size() - 1) {
					sb.append(',');
				}
			}
			sb.append(')');
		}

	}

}

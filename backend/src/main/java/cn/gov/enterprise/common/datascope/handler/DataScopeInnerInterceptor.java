package cn.gov.enterprise.common.datascope.handler;

import cn.gov.enterprise.common.datascope.context.DataPermissionContextHolder;
import cn.gov.enterprise.common.datascope.context.DataScopeInvocation;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import java.sql.SQLException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

/** 在MyBatis执行SELECT之前使用JSQLParser强制追加数据范围条件。 */
@Component
public class DataScopeInnerInterceptor extends JsqlParserSupport implements InnerInterceptor {
    private final DataScopeSqlHandler sqlHandler;

    public DataScopeInnerInterceptor(DataScopeSqlHandler sqlHandler) {
        this.sqlHandler = sqlHandler;
    }

    @Override
    public void beforeQuery(
            Executor executor, MappedStatement mappedStatement, Object parameter,
            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (mappedStatement.getSqlCommandType() != SqlCommandType.SELECT) return;
        DataPermissionContextHolder.current().ifPresent(invocation -> {
            PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
            mpBoundSql.sql(parserSingle(mpBoundSql.sql(), invocation));
        });
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object object) {
        DataScopeInvocation invocation = (DataScopeInvocation) object;
        String condition = sqlHandler.condition(invocation.context(), invocation.rule());
        if (condition == null) return;
        apply(select, condition);
    }

    private void apply(Select select, String condition) {
        if (select instanceof PlainSelect plainSelect) {
            Expression where = plainSelect.getWhere();
            try {
                Expression scoped = where == null
                        ? CCJSqlParserUtil.parseCondExpression(condition)
                        : CCJSqlParserUtil.parseCondExpression("(" + where + ") AND (" + condition + ")");
                plainSelect.setWhere(scoped);
                return;
            } catch (Exception exception) {
                throw new IllegalStateException("数据权限条件解析失败，查询已拒绝", exception);
            }
        }
        if (select instanceof ParenthesedSelect parenthesedSelect) {
            apply(parenthesedSelect.getSelect(), condition);
            return;
        }
        throw new IllegalStateException("数据权限暂不支持该SELECT结构，请使用外层普通SELECT包装");
    }
}

package com.hpcloud.maas.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;

import com.hpcloud.maas.common.model.alarm.AggregateFunction;
import com.hpcloud.maas.common.model.alarm.AlarmOperator;
import com.hpcloud.maas.common.model.alarm.AlarmSubExpression;
import com.hpcloud.maas.common.model.metric.MetricDefinition;
import com.hpcloud.maas.domain.model.SubAlarm;
import com.hpcloud.maas.domain.service.SubAlarmDAO;
import com.hpcloud.persistence.SqlStatements;

/**
 * SubAlarm DAO implementation.
 * 
 * @author Jonathan Halterman
 */
public class SubAlarmDAOImpl implements SubAlarmDAO {
  /**
   * This query works by matching the set of actual dimension names and values to those in the
   * table, grouping by the dimension id and counting them to ensure that the number of matched
   * dimensions equals the number of actual dimensions in the table for the subscription.
   */
  private static final String FIND_BY_METRIC_DEF_SQL = "select sa.* from sub_alarm sa, sub_alarm_dimension d "
      + "join (%s) v on d.dimension_name = v.dimension_name and d.value = v.value "
      + "where sa.id = d.sub_alarm_id and sa.namespace = :namespace and sa.metric_type = :metricType and sa.metric_subject %s "
      + "group by d.sub_alarm_id"; // having count(d.sub_alarm_id) = (select count(*) from
                                   // sub_alarm_dimension where sub_alarm_id = d.sub_alarm_id) //
                                   // Removed temporarily since we don't receive all of the expected
                                   // dimensions for collectd metrics

  private final DBI db;

  @Inject
  public SubAlarmDAOImpl(DBI db) {
    this.db = db;
  }

  @Override
  public List<SubAlarm> find(MetricDefinition metricDefinition) {
    Handle h = db.open();

    try {
      String unionAllStatement = SqlStatements.unionAllStatementFor(metricDefinition.dimensions,
          "dimension_name", "value");
      String sql = String.format(FIND_BY_METRIC_DEF_SQL, unionAllStatement,
          metricDefinition.subject == null ? "is null" : "= :metricSubject");

      Query<Map<String, Object>> query = h.createQuery(sql)
          .bind("namespace", metricDefinition.namespace)
          .bind("metricType", metricDefinition.type);
      if (metricDefinition.subject != null)
        query.bind("metricSubject", metricDefinition.subject);
      List<Map<String, Object>> rows = query.list();

      List<SubAlarm> subAlarms = new ArrayList<SubAlarm>(rows.size());
      for (Map<String, Object> row : rows) {
        String subAlarmId = (String) row.get("id");
        AggregateFunction function = AggregateFunction.valueOf((String) row.get("function"));
        AlarmOperator operator = AlarmOperator.valueOf((String) row.get("operator"));
        AlarmSubExpression subExpression = new AlarmSubExpression(function, metricDefinition,
            operator, (Double) row.get("threshold"), (Integer) row.get("period"),
            (Integer) row.get("periods"));
        SubAlarm subAlarm = new SubAlarm(subAlarmId, (String) row.get("alarm_id"), subExpression);
        subAlarms.add(subAlarm);
      }

      return subAlarms;
    } finally {
      h.close();
    }
  }
}

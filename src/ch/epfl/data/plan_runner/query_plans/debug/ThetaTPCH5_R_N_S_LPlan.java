package ch.epfl.data.plan_runner.query_plans.debug;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import ch.epfl.data.plan_runner.components.Component;
import ch.epfl.data.plan_runner.components.DataSourceComponent;
import ch.epfl.data.plan_runner.components.theta.ThetaJoinComponentFactory;
import ch.epfl.data.plan_runner.conversion.DateConversion;
import ch.epfl.data.plan_runner.conversion.DoubleConversion;
import ch.epfl.data.plan_runner.conversion.IntegerConversion;
import ch.epfl.data.plan_runner.conversion.NumericConversion;
import ch.epfl.data.plan_runner.conversion.StringConversion;
import ch.epfl.data.plan_runner.conversion.TypeConversion;
import ch.epfl.data.plan_runner.expressions.ColumnReference;
import ch.epfl.data.plan_runner.expressions.DateSum;
import ch.epfl.data.plan_runner.expressions.ValueExpression;
import ch.epfl.data.plan_runner.expressions.ValueSpecification;
import ch.epfl.data.plan_runner.operators.AggregateCountOperator;
import ch.epfl.data.plan_runner.operators.ProjectOperator;
import ch.epfl.data.plan_runner.operators.SelectOperator;
import ch.epfl.data.plan_runner.predicates.ComparisonPredicate;
import ch.epfl.data.plan_runner.query_plans.QueryBuilder;
import ch.epfl.data.plan_runner.query_plans.theta.ThetaQueryPlansParameters;

public class ThetaTPCH5_R_N_S_LPlan {
    private static Logger LOG = Logger.getLogger(ThetaTPCH5_R_N_S_LPlan.class);

    private static final IntegerConversion _ic = new IntegerConversion();

    private static final TypeConversion<Date> _dc = new DateConversion();
    private static final TypeConversion<String> _sc = new StringConversion();
    private static final NumericConversion<Double> _doubleConv = new DoubleConversion();

    private QueryBuilder _queryBuilder = new QueryBuilder();

    // query variables
    private static Date _date1, _date2;
    // private static final String REGION_NAME = "ASIA";
    private static final String REGION_NAME = "AMERICA";

    private static void computeDates() {
	// date2 = date1 + 1 year
	String date1Str = "1994-01-01";
	int interval = 1;
	int unit = Calendar.YEAR;

	// setting _date1
	_date1 = _dc.fromString(date1Str);

	// setting _date2
	ValueExpression<Date> date1Ve, date2Ve;
	date1Ve = new ValueSpecification<Date>(_dc, _date1);
	date2Ve = new DateSum(date1Ve, unit, interval);
	_date2 = date2Ve.eval(null);
	// tuple is set to null since we are computing based on constants
    }

    public ThetaTPCH5_R_N_S_LPlan(String dataPath, String extension, Map conf) {
	computeDates();
	int Theta_JoinType = ThetaQueryPlansParameters.getThetaJoinType(conf);
	// -------------------------------------------------------------------------------------
	List<Integer> hashRegion = Arrays.asList(0);

	SelectOperator selectionRegion = new SelectOperator(
		new ComparisonPredicate(new ColumnReference(_sc, 1),
			new ValueSpecification(_sc, REGION_NAME)));

	ProjectOperator projectionRegion = new ProjectOperator(new int[] { 0 });

	DataSourceComponent relationRegion = new DataSourceComponent("REGION",
		dataPath + "region" + extension).setOutputPartKey(hashRegion)
		.add(selectionRegion).add(projectionRegion);
	_queryBuilder.add(relationRegion);

	// -------------------------------------------------------------------------------------
	List<Integer> hashNation = Arrays.asList(2);

	ProjectOperator projectionNation = new ProjectOperator(new int[] { 0,
		1, 2 });

	DataSourceComponent relationNation = new DataSourceComponent("NATION",
		dataPath + "nation" + extension).setOutputPartKey(hashNation)
		.add(projectionNation);
	_queryBuilder.add(relationNation);

	// -------------------------------------------------------------------------------------
	List<Integer> hashRN = Arrays.asList(0);

	ProjectOperator projectionRN = new ProjectOperator(new int[] { 1, 2 });

	ColumnReference colR = new ColumnReference(_ic, 0);
	ColumnReference colN = new ColumnReference(_ic, 2);
	ComparisonPredicate R_N_comp = new ComparisonPredicate(
		ComparisonPredicate.EQUAL_OP, colR, colN);

	Component R_Njoin = ThetaJoinComponentFactory
		.createThetaJoinOperator(Theta_JoinType, relationRegion,
			relationNation, _queryBuilder).setOutputPartKey(hashRN)
		.add(projectionRN).setJoinPredicate(R_N_comp);

	// -------------------------------------------------------------------------------------
	List<Integer> hashSupplier = Arrays.asList(1);

	ProjectOperator projectionSupplier = new ProjectOperator(new int[] { 0,
		3 });

	DataSourceComponent relationSupplier = new DataSourceComponent(
		"SUPPLIER", dataPath + "supplier" + extension)
		.setOutputPartKey(hashSupplier).add(projectionSupplier);
	_queryBuilder.add(relationSupplier);

	// -------------------------------------------------------------------------------------
	List<Integer> hashRNS = Arrays.asList(2);

	ProjectOperator projectionRNS = new ProjectOperator(
		new int[] { 0, 1, 2 });

	ColumnReference colR_N = new ColumnReference(_ic, 0);
	ColumnReference colS = new ColumnReference(_ic, 1);
	ComparisonPredicate R_N_S_comp = new ComparisonPredicate(
		ComparisonPredicate.EQUAL_OP, colR_N, colS);

	Component R_N_Sjoin = ThetaJoinComponentFactory
		.createThetaJoinOperator(Theta_JoinType, R_Njoin,
			relationSupplier, _queryBuilder)
		.setOutputPartKey(hashRNS).add(projectionRNS)
		.setJoinPredicate(R_N_S_comp);

	// -------------------------------------------------------------------------------------
	List<Integer> hashLineitem = Arrays.asList(1);

	ProjectOperator projectionLineitem = new ProjectOperator(new int[] { 0,
		2, 5, 6 });

	DataSourceComponent relationLineitem = new DataSourceComponent(
		"LINEITEM", dataPath + "lineitem" + extension)
		.setOutputPartKey(hashLineitem).add(projectionLineitem);
	_queryBuilder.add(relationLineitem);

	// -------------------------------------------------------------------------------------
	List<Integer> hashRNSL = Arrays.asList(0, 2);

	ColumnReference colR_N_S = new ColumnReference(_ic, 2);
	ColumnReference colL = new ColumnReference(_ic, 1);
	ComparisonPredicate R_N_S_L_comp = new ComparisonPredicate(
		ComparisonPredicate.EQUAL_OP, colR_N_S, colL);

	ProjectOperator projectionRNSL = new ProjectOperator(new int[] { 0, 1,
		3, 5, 6 });

	// AggregateCountOperator agg= new AggregateCountOperator(conf);

	AggregateCountOperator agg = new AggregateCountOperator(conf);
	Component R_N_S_Ljoin = ThetaJoinComponentFactory
		.createThetaJoinOperator(Theta_JoinType, R_N_Sjoin,
			relationLineitem, _queryBuilder)
		.setOutputPartKey(hashRNSL).add(projectionRNSL)
		.setJoinPredicate(R_N_S_L_comp)
		.setContentSensitiveThetaJoinWrapper(_ic).add(agg);

	// R_N_S_Ljoin.setPrintOut(false);
	// -------------------------------------------------------------------------------------

    }

    public QueryBuilder getQueryPlan() {
	return _queryBuilder;
    }
}
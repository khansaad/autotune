package com.autotune.database.dao;

import com.autotune.analyzer.kruizeObject.KruizeObject;
import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.analyzer.utils.AnalyzerErrorConstants;
import com.autotune.common.data.ValidationOutputData;
import com.autotune.database.helper.DBConstants;
import com.autotune.database.init.KruizeHibernateUtil;
import com.autotune.database.table.KruizeExperimentEntry;
import com.autotune.database.table.KruizePerformanceProfileEntry;
import com.autotune.database.table.KruizeRecommendationEntry;
import com.autotune.database.table.KruizeResultsEntry;
import com.autotune.utils.KruizeConstants;
import com.autotune.utils.MetricsConfig;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import jakarta.persistence.EntityManager;
import org.hibernate.*;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import static com.autotune.database.helper.DBConstants.SQLQUERY.*;

public class ExperimentDAOImpl implements ExperimentDAO {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExperimentDAOImpl.class);

    @Override
    public ValidationOutputData addExperimentToDB(KruizeExperimentEntry kruizeExperimentEntry) {
        ValidationOutputData validationOutputData = new ValidationOutputData(false, null, null);
        Transaction tx = null;
        String statusValue = "failure";
        Timer.Sample timerAddExpDB = Timer.start(MetricsConfig.meterRegistry());
        try {
            try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
                try {
                    tx = session.beginTransaction();
                    session.persist(kruizeExperimentEntry);
                    tx.commit();
                    validationOutputData.setSuccess(true);
                    statusValue = "success";
                } catch (HibernateException e) {
                    LOGGER.error("Not able to save experiment due to {}", e.getMessage());
                    if (tx != null) tx.rollback();
                    e.printStackTrace();
                    validationOutputData.setSuccess(false);
                    validationOutputData.setMessage(e.getMessage());
                    //TODO: save error to API_ERROR_LOG
                }
            }
        } catch (Exception e) {
            LOGGER.error("Not able to save experiment due to {}", e.getMessage());
            validationOutputData.setMessage(e.getMessage());
        } finally {
            if (null != timerAddExpDB) {
                MetricsConfig.timerAddExpDB = MetricsConfig.timerBAddExpDB.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerAddExpDB.stop(MetricsConfig.timerAddExpDB);
            }
        }
        return validationOutputData;
    }

    @Override
    public void addPartitions(String tableName, String month, String year, int dayOfTheMonth, String partitionType) {
        Transaction tx;
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            // Create a YearMonth object and get the current month and current year
            YearMonth yearMonth = YearMonth.of(Integer.parseInt(year), Integer.parseInt(month));

            // check the partition type and create corresponding query
            if (partitionType.equalsIgnoreCase(DBConstants.PARTITION_TYPES.BY_MONTH)) {
                // Get the last day of the month
                int lastDayOfMonth = yearMonth.lengthOfMonth();
                IntStream.range(dayOfTheMonth, lastDayOfMonth + 1).forEach(i -> {
                    String daterange = String.format(DB_PARTITION_DATERANGE, tableName, year, month, String.format("%02d", i), tableName,
                            year, month, String.format("%02d", i), year, month, String.format("%02d", i));
                    session.createNativeQuery(daterange).executeUpdate();
                });
            } else if (partitionType.equalsIgnoreCase(DBConstants.PARTITION_TYPES.BY_15_DAYS)) {
                    IntStream.range(1, 16).forEach(i -> {
                        String daterange = String.format(DB_PARTITION_DATERANGE, tableName, year, month, String.format("%02d", i), tableName,
                                year, month, String.format("%02d", i), year, month, String.format("%02d", i));
                        session.createNativeQuery(daterange).executeUpdate();
                    });
            } else if (partitionType.equalsIgnoreCase(DBConstants.PARTITION_TYPES.BY_DAY)) {
                String daterange = String.format(DB_PARTITION_DATERANGE, tableName, year, month, String.format("%02d", 1), tableName,
                        year, month, String.format("%02d", 1), year, month, String.format("%02d", 1));
                session.createNativeQuery(daterange).executeUpdate();
            } else {
                LOGGER.error(DBConstants.DB_MESSAGES.INVALID_PARTITION_TYPE);
                throw new Exception(DBConstants.DB_MESSAGES.INVALID_PARTITION_TYPE);
            }

            tx.commit();
        } catch (Exception e) {
            LOGGER.error("Exception occurred while adding the partition: {}", e.getMessage());
        }
    }

    @Override
    public ValidationOutputData addResultsToDB(KruizeResultsEntry resultsEntry) {
        ValidationOutputData validationOutputData = new ValidationOutputData(false, null, null);
        Transaction tx = null;
        String statusValue = "failure";
        Timer.Sample timerAddResultsDB = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            try {
                tx = session.beginTransaction();
                session.persist(resultsEntry);
                tx.commit();
                validationOutputData.setSuccess(true);
                statusValue = "success";
            } catch (PersistenceException ex) {
                if (ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                    validationOutputData.setSuccess(false);
                    validationOutputData.setMessage(
                            String.format(DBConstants.DB_MESSAGES.RECORD_ALREADY_EXISTS, resultsEntry.getExperiment_name(),
                                    resultsEntry.getInterval_start_time(), resultsEntry.getInterval_end_time())
                    );
                } else {
                    throw new Exception(ex.getMessage());
                }
            } catch (Exception e) {
                LOGGER.error("Not able to save experiment due to {}", e.getMessage());
                if (tx != null) tx.rollback();
                e.printStackTrace();
                validationOutputData.setSuccess(false);
                validationOutputData.setMessage(e.getMessage());
                //todo save error to API_ERROR_LOG
            }
        } catch (Exception e) {
            LOGGER.error("Not able to save experiment due to {}", e.getMessage());
        } finally {
            if (null != timerAddResultsDB) {
                MetricsConfig.timerAddResultsDB = MetricsConfig.timerBAddResultsDB.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerAddResultsDB.stop(MetricsConfig.timerAddResultsDB);
            }
        }
        return validationOutputData;
    }


    @Override
    public List<KruizeResultsEntry> addToDBAndFetchFailedResults(List<KruizeResultsEntry> kruizeResultsEntries) {
        List<KruizeResultsEntry> failedResultsEntries = new ArrayList<>();
        Transaction tx = null;
        String statusValue = "failure";
        Timer.Sample timerAddBulkResultsDB = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            for (KruizeResultsEntry entry : kruizeResultsEntries) {
                try {
                    session.persist(entry);
                    session.flush();
                } catch (PersistenceException e) {
                    ConstraintViolationException constraintViolationException = (ConstraintViolationException) e.getCause();
                    String message = constraintViolationException.getCause().getMessage();
                    LOGGER.error(message);
                    if (message.contains(DBConstants.DB_MESSAGES.DUPLICATE_KEY)) {
                        entry.setErrorReasons(List.of(AnalyzerErrorConstants.APIErrors.updateResultsAPI.RESULTS_ALREADY_EXISTS));
                        failedResultsEntries.add(entry);
                    } else if (message.contains(DBConstants.DB_MESSAGES.NO_PARTITION_RELATION)) {
                        try {
                            LOGGER.info(DBConstants.DB_MESSAGES.CREATE_PARTITION_RETRY);
                            tx.commit();
                            tx = session.beginTransaction();
                            LocalDateTime localDateTime = entry.getInterval_end_time().toLocalDateTime();
                            // Get the current year and month
                            YearMonth yearMonth = YearMonth.from(localDateTime);
                            // Format the month with a leading zero if it's a single digit
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM");
                            // Format the year and month
                            String formattedYear = String.valueOf(yearMonth.getYear());
                            String formattedMonth = yearMonth.format(formatter);
                            int dayOfTheMonth = localDateTime.getDayOfMonth();
                            // Fixing the partition type to 'by_month'
                            addPartitions(DBConstants.TABLE_NAMES.KRUIZE_RESULTS, formattedMonth,formattedYear, dayOfTheMonth, DBConstants.PARTITION_TYPES.BY_MONTH);
                            addPartitions(DBConstants.TABLE_NAMES.KRUIZE_RECOMMENDATIONS, formattedMonth, formattedYear, dayOfTheMonth, DBConstants.PARTITION_TYPES.BY_MONTH);
                            session.persist(entry);
                            session.flush();
                        } catch (Exception partitionException) {
                            LOGGER.error(partitionException.getMessage());
                            entry.setErrorReasons(List.of(partitionException.getMessage()));
                            failedResultsEntries.add(entry);
                        }
                    } else {
                        entry.setErrorReasons(List.of(e.getMessage()));
                        failedResultsEntries.add(entry);
                    }
                    tx.commit();
                    tx = session.beginTransaction();
                } catch (Exception e) {
                    entry.setErrorReasons(List.of(e.getMessage()));
                    failedResultsEntries.add(entry);
                    tx.commit();
                    tx = session.beginTransaction();
                }
            }
            tx.commit();


            statusValue = "success";
        } catch (Exception e) {
            LOGGER.error("Not able to save experiment due to {}", e.getMessage());
            failedResultsEntries.addAll(kruizeResultsEntries);
            failedResultsEntries.forEach((entry) -> {
                entry.setErrorReasons(List.of(e.getMessage()));
            });
        } finally {
            if (null != timerAddBulkResultsDB) {
                MetricsConfig.timerAddBulkResultsDB = MetricsConfig.timerBAddBulkResultsDB.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerAddBulkResultsDB.stop(MetricsConfig.timerAddBulkResultsDB);
            }
        }
        return failedResultsEntries;
    }

    @Override
    public ValidationOutputData addRecommendationToDB(KruizeRecommendationEntry recommendationEntry) {
        ValidationOutputData validationOutputData = new ValidationOutputData(false, null, null);
        Transaction tx = null;
        String statusValue = "failure";
        Timer.Sample timerAddRecDB = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            try {
                KruizeRecommendationEntry existingRecommendationEntry = loadRecommendationsByExperimentNameAndDate(recommendationEntry.getExperiment_name(), recommendationEntry.getCluster_name(), recommendationEntry.getInterval_end_time());
                if (null == existingRecommendationEntry) {
                    tx = session.beginTransaction();
                    session.persist(recommendationEntry);
                    tx.commit();
                    validationOutputData.setSuccess(true);
                    statusValue = "success";
                } else {
                    tx = session.beginTransaction();
                    existingRecommendationEntry.setExtended_data(recommendationEntry.getExtended_data());
                    session.merge(existingRecommendationEntry);
                    tx.commit();
                    validationOutputData.setSuccess(true);
                    statusValue = "success";
                }
            } catch (Exception e) {
                LOGGER.error("Not able to save recommendation due to {}", e.getMessage());
                if (tx != null) tx.rollback();
                e.printStackTrace();
                validationOutputData.setSuccess(false);
                validationOutputData.setMessage(e.getMessage());
                //todo save error to API_ERROR_LOG
            }
        } catch (Exception e) {
            LOGGER.error("Not able to save recommendation due to {}", e.getMessage());
        } finally {
            if (null != timerAddRecDB) {
                MetricsConfig.timerAddRecDB = MetricsConfig.timerBAddRecDB.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerAddRecDB.stop(MetricsConfig.timerAddRecDB);
            }
        }
        return validationOutputData;
    }

    @Override
    public ValidationOutputData addPerformanceProfileToDB(KruizePerformanceProfileEntry kruizePerformanceProfileEntry) {
        ValidationOutputData validationOutputData = new ValidationOutputData(false, null, null);
        String statusValue = "failure";
        Timer.Sample timerAddPerfProfileDB = Timer.start(MetricsConfig.meterRegistry());
        Transaction tx = null;
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            try {
                tx = session.beginTransaction();
                session.persist(kruizePerformanceProfileEntry);
                tx.commit();
                validationOutputData.setSuccess(true);
                statusValue = "success";
            } catch (HibernateException e) {
                LOGGER.error("Not able to save performance profile due to {}", e.getMessage());
                if (tx != null) tx.rollback();
                e.printStackTrace();
                validationOutputData.setSuccess(false);
                validationOutputData.setMessage(e.getMessage());
                //todo save error to API_ERROR_LOG
            }
        } catch (Exception e) {
            LOGGER.error("Not able to save performance profile due to {}", e.getMessage());
            validationOutputData.setMessage(e.getMessage());
        } finally {
            if (null != timerAddPerfProfileDB) {
                MetricsConfig.timerAddPerfProfileDB = MetricsConfig.timerBAddPerfProfileDB.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerAddPerfProfileDB.stop(MetricsConfig.timerAddPerfProfileDB);
            }
        }
        return validationOutputData;
    }


    @Override
    public boolean updateExperimentStatus(KruizeObject kruizeObject, AnalyzerConstants.ExperimentStatus status) {
        kruizeObject.setStatus(status);
        // TODO   update into database
        return true;
    }

    /**
     * Delete an experiment with the name experimentName
     * This deletes the experiment from all three tables
     * kruize_experiments, kruize_results and kruize_recommendations
     * Delete from kruize_results and kruize_recommendations only if the delete from kruize_experiments succeeds.
     *
     * @param experimentName
     * @return
     */
    @Override
    public ValidationOutputData deleteKruizeExperimentEntryByName(String experimentName) {
        ValidationOutputData validationOutputData = new ValidationOutputData(false, null, null);
        Transaction tx = null;
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            try {
                tx = session.beginTransaction();
                Query query = session.createQuery(DELETE_FROM_EXPERIMENTS_BY_EXP_NAME, null);
                query.setParameter("experimentName", experimentName);
                int deletedCount = query.executeUpdate();
                if (deletedCount == 0) {
                    validationOutputData.setSuccess(false);
                    validationOutputData.setMessage("KruizeExperimentEntry not found with experiment name: " + experimentName);
                } else {
                    // Remove the experiment from the Results table
                    Query kruizeResultsEntryquery = session.createQuery(DELETE_FROM_RESULTS_BY_EXP_NAME, null);
                    kruizeResultsEntryquery.setParameter("experimentName", experimentName);
                    kruizeResultsEntryquery.executeUpdate();

                    // Remove the experiment from the Recommendations table
                    Query kruizeRecommendationEntryquery = session.createQuery(DELETE_FROM_RECOMMENDATIONS_BY_EXP_NAME, null);
                    kruizeRecommendationEntryquery.setParameter("experimentName", experimentName);
                    kruizeRecommendationEntryquery.executeUpdate();
                    validationOutputData.setSuccess(true);
                }
                tx.commit();
            } catch (HibernateException e) {
                LOGGER.error("Not able to delete experiment {} due to {}", experimentName, e.getMessage());
                if (tx != null) tx.rollback();
                e.printStackTrace();
                validationOutputData.setSuccess(false);
                validationOutputData.setMessage(e.getMessage());
                //todo save error to API_ERROR_LOG
            }
        } catch (Exception e) {
            LOGGER.error("Not able to delete experiment {} due to {}", experimentName, e.getMessage());
        }
        return validationOutputData;
    }

    @Override
    public List<KruizeExperimentEntry> loadAllExperiments() throws Exception {
        //todo load only experimentStatus=inprogress , playback may not require completed experiments
        List<KruizeExperimentEntry> entries = null;
        String statusValue = "failure";
        Timer.Sample timerLoadAllExp = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            entries = session.createQuery(DBConstants.SQLQUERY.SELECT_FROM_EXPERIMENTS, KruizeExperimentEntry.class).list();
            statusValue = "success";
        } catch (Exception e) {
            LOGGER.error("Not able to load experiment due to {}", e.getMessage());
            throw new Exception("Error while loading existing experiments from database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadAllExp) {
                MetricsConfig.timerLoadAllExp = MetricsConfig.timerBLoadAllExp.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadAllExp.stop(MetricsConfig.timerLoadAllExp);
            }
        }
        return entries;
    }

    @Override
    public List<KruizeResultsEntry> loadAllResults() throws Exception {
        // TODO: load only experimentStatus=inProgress , playback may not require completed experiments
        List<KruizeResultsEntry> kruizeResultsEntries = null;
        String statusValue = "failure";
        Timer.Sample timerLoadAllResults = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            kruizeResultsEntries = session.createQuery(DBConstants.SQLQUERY.SELECT_FROM_RESULTS, KruizeResultsEntry.class).list();
            statusValue = "success";
        } catch (Exception e) {
            LOGGER.error("Not able to load results due to: {}", e.getMessage());
            throw new Exception("Error while loading results from the database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadAllResults) {
                MetricsConfig.timerLoadAllResults = MetricsConfig.timerBLoadAllResults.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadAllResults.stop(MetricsConfig.timerLoadAllResults);
            }

        }
        return kruizeResultsEntries;
    }

    @Override
    public List<KruizeRecommendationEntry> loadAllRecommendations() throws Exception {
        List<KruizeRecommendationEntry> recommendationEntries = null;
        String statusValue = "failure";
        Timer.Sample timerLoadAllRec = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            recommendationEntries = session.createQuery(
                    DBConstants.SQLQUERY.SELECT_FROM_RECOMMENDATIONS,
                    KruizeRecommendationEntry.class).list();
            statusValue = "success";
        } catch (Exception e) {
            LOGGER.error("Not able to load recommendations due to {}", e.getMessage());
            throw new Exception("Error while loading existing recommendations from database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadAllRec) {
                MetricsConfig.timerLoadAllRec = MetricsConfig.timerBLoadAllRec.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadAllRec.stop(MetricsConfig.timerLoadAllRec);
            }
        }
        return recommendationEntries;
    }

    @Override
    public List<KruizePerformanceProfileEntry> loadAllPerformanceProfiles() throws Exception {
        String statusValue = "failure";
        Timer.Sample timerLoadAllPerfProfiles = Timer.start(MetricsConfig.meterRegistry());
        List<KruizePerformanceProfileEntry> entries = null;
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            entries = session.createQuery(DBConstants.SQLQUERY.SELECT_FROM_PERFORMANCE_PROFILE, KruizePerformanceProfileEntry.class).list();
        } catch (Exception e) {
            LOGGER.error("Not able to load Performance Profile  due to {}", e.getMessage());
            throw new Exception("Error while loading existing Performance Profile from database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadAllPerfProfiles) {
                MetricsConfig.timerLoadAllPerfProfiles = MetricsConfig.timerBLoadAllPerfProfiles.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadAllPerfProfiles.stop(MetricsConfig.timerLoadAllPerfProfiles);
            }
        }
        return entries;
    }

    @Override
    public List<KruizeExperimentEntry> loadExperimentByName(String experimentName) throws Exception {
        //todo load only experimentStatus=inprogress , playback may not require completed experiments
        List<KruizeExperimentEntry> entries = null;
        String statusValue = "failure";
        Timer.Sample timerLoadExpName = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            entries = session.createQuery(DBConstants.SQLQUERY.SELECT_FROM_EXPERIMENTS_BY_EXP_NAME, KruizeExperimentEntry.class)
                    .setParameter("experimentName", experimentName).list();
            statusValue = "success";
        } catch (Exception e) {
            LOGGER.error("Not able to load experiment {} due to {}", experimentName, e.getMessage());
            throw new Exception("Error while loading existing experiment from database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadExpName) {
                MetricsConfig.timerLoadExpName = MetricsConfig.timerBLoadExpName.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadExpName.stop(MetricsConfig.timerLoadExpName);
            }

        }
        return entries;
    }

    @Override
    public List<KruizeResultsEntry> loadResultsByExperimentName(String experimentName, String cluster_name, Timestamp interval_end_time, Integer limitRows) throws Exception {
        // TODO: load only experimentStatus=inProgress , playback may not require completed experiments
        List<KruizeResultsEntry> kruizeResultsEntries = null;
        String statusValue = "failure";
        Timer.Sample timerLoadResultsExpName = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            if (null != limitRows && null != interval_end_time) {
                kruizeResultsEntries = session.createQuery(DBConstants.SQLQUERY.SELECT_FROM_RESULTS_BY_EXP_NAME_AND_DATE_RANGE_AND_LIMIT, KruizeResultsEntry.class)
                        .setParameter(KruizeConstants.JSONKeys.CLUSTER_NAME, cluster_name)
                        .setParameter(KruizeConstants.JSONKeys.EXPERIMENT_NAME, experimentName)
                        .setParameter(KruizeConstants.JSONKeys.INTERVAL_END_TIME, interval_end_time)
                        .setMaxResults(limitRows)
                        .list();
                statusValue = "success";
            } else {
                kruizeResultsEntries = session.createQuery(DBConstants.SQLQUERY.SELECT_FROM_RESULTS_BY_EXP_NAME, KruizeResultsEntry.class)
                        .setParameter("experimentName", experimentName).list();
                statusValue = "success";
            }
        } catch (Exception e) {
            LOGGER.error("Not able to load results due to: {}", e.getMessage());
            throw new Exception("Error while loading results from the database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadResultsExpName) {
                MetricsConfig.timerLoadResultsExpName = MetricsConfig.timerBLoadResultsExpName.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadResultsExpName.stop(MetricsConfig.timerLoadResultsExpName);
            }
        }
        return kruizeResultsEntries;
    }

    @Override
    public List<KruizeRecommendationEntry> loadRecommendationsByExperimentName(String experimentName) throws Exception {
        List<KruizeRecommendationEntry> recommendationEntries = null;
        String statusValue = "failure";
        Timer.Sample timerLoadRecExpName = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            recommendationEntries = session.createQuery(DBConstants.SQLQUERY.SELECT_FROM_RECOMMENDATIONS_BY_EXP_NAME, KruizeRecommendationEntry.class)
                    .setParameter("experimentName", experimentName).list();
            statusValue = "success";
        } catch (Exception e) {
            LOGGER.error("Not able to load recommendations due to {}", e.getMessage());
            throw new Exception("Error while loading existing recommendations from database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadRecExpName) {
                MetricsConfig.timerLoadRecExpName = MetricsConfig.timerBLoadRecExpName.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadRecExpName.stop(MetricsConfig.timerLoadRecExpName);
            }
        }
        return recommendationEntries;
    }

    @Override
    public KruizeRecommendationEntry loadRecommendationsByExperimentNameAndDate(String experimentName, String cluster_name, Timestamp interval_end_time) throws Exception {
        KruizeRecommendationEntry recommendationEntries = null;
        String statusValue = "failure";
        Timer.Sample timerLoadRecExpNameDate = Timer.start(MetricsConfig.meterRegistry());
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            recommendationEntries = session.createQuery(SELECT_FROM_RECOMMENDATIONS_BY_EXP_NAME_AND_END_TIME, KruizeRecommendationEntry.class)
                    .setParameter(KruizeConstants.JSONKeys.CLUSTER_NAME, cluster_name)
                    .setParameter(KruizeConstants.JSONKeys.EXPERIMENT_NAME, experimentName)
                    .setParameter(KruizeConstants.JSONKeys.INTERVAL_END_TIME, interval_end_time)
                    .getSingleResult();
            statusValue = "success";
        } catch (NoResultException e) {
            LOGGER.debug("Generating mew recommendation for Experiment name : %s interval_end_time: %S", experimentName, interval_end_time);
        } catch (Exception e) {
            LOGGER.error("Not able to load recommendations due to {}", e.getMessage());
            recommendationEntries = null;
            throw new Exception("Error while loading existing recommendations from database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadRecExpNameDate) {
                MetricsConfig.timerLoadRecExpNameDate = MetricsConfig.timerBLoadRecExpNameDate.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadRecExpNameDate.stop(MetricsConfig.timerLoadRecExpNameDate);
            }
        }
        return recommendationEntries;
    }


    public List<KruizePerformanceProfileEntry> loadPerformanceProfileByName(String performanceProfileName) throws Exception {
        String statusValue = "failure";
        Timer.Sample timerLoadPerfProfileName = Timer.start(MetricsConfig.meterRegistry());
        List<KruizePerformanceProfileEntry> entries = null;
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            entries = session.createQuery(DBConstants.SQLQUERY.SELECT_FROM_PERFORMANCE_PROFILE_BY_NAME, KruizePerformanceProfileEntry.class)
                    .setParameter("name", performanceProfileName).list();
        } catch (Exception e) {
            LOGGER.error("Not able to load Performance Profile {} due to {}", performanceProfileName, e.getMessage());
            throw new Exception("Error while loading existing profile from database due to : " + e.getMessage());
        } finally {
            if (null != timerLoadPerfProfileName) {
                MetricsConfig.timerLoadPerfProfileName = MetricsConfig.timerBLoadPerfProfileName.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerLoadPerfProfileName.stop(MetricsConfig.timerLoadPerfProfileName);
            }
        }
        return entries;
    }


    @Override
    public List<KruizeResultsEntry> getKruizeResultsEntry(String experiment_name, String cluster_name, Timestamp interval_start_time, Timestamp interval_end_time) throws Exception {
        List<KruizeResultsEntry> kruizeResultsEntryList = new ArrayList<>();
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {

            if (interval_start_time != null && interval_end_time != null) {
                kruizeResultsEntryList = session.createQuery(SELECT_FROM_RESULTS_BY_EXP_NAME_AND_START_END_TIME, KruizeResultsEntry.class)
                        .setParameter(KruizeConstants.JSONKeys.CLUSTER_NAME, cluster_name)
                        .setParameter(KruizeConstants.JSONKeys.EXPERIMENT_NAME, experiment_name)
                        .setParameter(KruizeConstants.JSONKeys.INTERVAL_START_TIME, interval_start_time)
                        .setParameter(KruizeConstants.JSONKeys.INTERVAL_END_TIME, interval_end_time)
                        .getResultList();
            } else if (interval_end_time != null) {
                kruizeResultsEntryList = session.createQuery(SELECT_FROM_RESULTS_BY_EXP_NAME_AND_END_TIME, KruizeResultsEntry.class)
                        .setParameter(KruizeConstants.JSONKeys.CLUSTER_NAME, cluster_name)
                        .setParameter(KruizeConstants.JSONKeys.EXPERIMENT_NAME, experiment_name)
                        .setParameter(KruizeConstants.JSONKeys.INTERVAL_END_TIME, interval_end_time)
                        .getResultList();
            } else {
                kruizeResultsEntryList = session.createQuery(SELECT_FROM_RESULTS_BY_EXP_NAME_AND_MAX_END_TIME, KruizeResultsEntry.class)
                        .setParameter(KruizeConstants.JSONKeys.CLUSTER_NAME, cluster_name)
                        .setParameter(KruizeConstants.JSONKeys.EXPERIMENT_NAME, experiment_name)
                        .getResultList();
            }
        } catch (NoResultException e) {
            LOGGER.error(DBConstants.DB_MESSAGES.DATA_NOT_FOUND_KRUIZE_RESULTS, experiment_name, interval_end_time);
            kruizeResultsEntryList = null;
        } catch (Exception e) {
            kruizeResultsEntryList = null;
            LOGGER.error("Not able to load results due to: {}", e.getMessage());
            throw new Exception("Error while loading results from the database due to : " + e.getMessage());
        }
        return kruizeResultsEntryList;
    }



    @Override
    public List<String> loadAllClusterNames() throws Exception {
        List<String> distinctClusterNames;
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            Query<String> query = session.createQuery(DBConstants.SQLQUERY.SELECT_DISTINCT_CLUSTER_NAMES_FROM_EXPERIMENTS,
                    String.class);
            distinctClusterNames = query.getResultList();
        } catch (Exception e) {
            LOGGER.error("Unable to fetch cluster names : {}", e.getMessage());
            throw new Exception("Error while fetching the cluster names from database due to : " + e.getMessage());
        }
        return distinctClusterNames;
    }

    @Override
    public List<KruizeRecommendationEntry> loadRecommendationsFromDBByNamespaceName(String namespaceName, List<KruizeExperimentEntry> entries) throws Exception {
        List<KruizeRecommendationEntry> recommendationEntries = new ArrayList<>();
        ArrayList<String> experimentNames = new ArrayList<>();
        for (KruizeExperimentEntry entry : entries) {
            experimentNames.add(entry.getExperiment_name());
        }
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {

            String namespaceFilter = "[{\"namespace\": \"" + namespaceName + "\"}]";    //TODO: need to think of a better way to set up this query

            ScrollableResults<KruizeRecommendationEntry> results = session.createNativeQuery(SELECT_FROM_RECOMMENDATIONS_BY_NAMESPACE_NAME, KruizeRecommendationEntry.class)
                    .setParameter("namespaceFilter", namespaceFilter)
                    .setParameterList("experimentNames", experimentNames)
                    .setParameter("limit", experimentNames.size())
                    .scroll();
            while (results.next()) {
                KruizeRecommendationEntry recommendationEntry = results.get();
                Hibernate.initialize(recommendationEntry.getExtended_data());

                recommendationEntries.add(recommendationEntry);
            }
        } catch (Exception e) {
            LOGGER.error("Not able to load recommendations due to {}", e.getMessage());
            throw new Exception("Error while loading recommendations from database due to : " + e.getMessage());
        }
        return recommendationEntries;
    }

    @Override
    public List<KruizeExperimentEntry> loadExperimentsByClusterName(String clusterName) throws Exception {
        List<KruizeExperimentEntry> entries;
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            entries = session.createQuery(SELECT_FROM_EXPERIMENTS_BY_CLUSTER_NAME, KruizeExperimentEntry.class)
                    .setParameter("clusterName", clusterName)
                    .list();
        } catch (Exception e) {
            LOGGER.error("Not able to load experiment due to {}", e.getMessage());
            throw new Exception("Error while loading existing experiment from database due to : " + e.getMessage());
        }
        return entries;
    }

    @Override
    public List<KruizeExperimentEntry> loadExperimentFromDBByClusterAndNamespaceName(String clusterName, String namespaceName) throws Exception {
        List<KruizeExperimentEntry> entries = new ArrayList<>();
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {

            String namespaceFilter = "[{\"namespace\": \"" + namespaceName + "\"}]";    //TODO: need to think of a better way to set up this query

            ScrollableResults<KruizeExperimentEntry> results = session.createNativeQuery(SELECT_FROM_EXPERIMENTS_BY_NAMESPACE_AND_CLUSTER_NAME, KruizeExperimentEntry.class)
                    .setParameter("clusterName", clusterName)
                    .setParameter("namespaceFilter", namespaceFilter)
                    .setMaxResults(BATCH_SIZE)
                    .scroll();
            while (results.next()) {
                KruizeExperimentEntry experimentEntry = results.get();
                Hibernate.initialize(experimentEntry.getExtended_data());

                entries.add(experimentEntry);
            }
        } catch (Exception e) {
            LOGGER.error("Not able to load experiment due to {}", e.getMessage());
            throw new Exception("Error while loading existing experiment from database due to : " + e.getMessage());
        }
        return entries;
    }

    @Override
    public List<KruizeRecommendationEntry> loadRecommendationsFromDBByClusterAndNamespaceName(String clusterName, String namespaceName,
                                                                                              List<KruizeExperimentEntry> entries) throws Exception {

        List<KruizeRecommendationEntry> recommendationEntries = new ArrayList<>();
        ArrayList<String> experimentNames = new ArrayList<>();
        for (KruizeExperimentEntry entry : entries) {
            experimentNames.add(entry.getExperiment_name());
        }
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {

            String namespaceFilter = "[{\"namespace\": \"" + namespaceName + "\"}]";    //TODO: need to think of a better way to set up this query

            ScrollableResults<KruizeRecommendationEntry> results = session.createNativeQuery(SELECT_FROM_RECOMMENDATIONS_BY_NAMESPACE_AND_CLUSTER_NAME, KruizeRecommendationEntry.class)
                    .setParameter("clusterName", clusterName)
                    .setParameter("namespaceFilter", namespaceFilter)
                    .setParameterList("experimentNames", experimentNames)
                    .setParameter("limit", experimentNames.size())
                    .scroll();
            while (results.next()) {
                KruizeRecommendationEntry recommendationEntry = results.get();
                Hibernate.initialize(recommendationEntry.getExtended_data());

                recommendationEntries.add(recommendationEntry);
            }
        } catch (Exception e) {
            LOGGER.error("Not able to load recommendations due to {}", e.getMessage());
            throw new Exception("Error while loading existing recommendations from database due to : " + e.getMessage());
        }
        return recommendationEntries;
    }

    @Override
    public List<KruizeRecommendationEntry> loadRecommendationsFromDBByClusterName(String clusterName, List<KruizeExperimentEntry> entries) throws Exception {
        List<KruizeRecommendationEntry> recommendationEntries = new ArrayList<>();
        ArrayList<String> experimentNames = new ArrayList<>();
        for (KruizeExperimentEntry entry : entries) {
            experimentNames.add(entry.getExperiment_name());
        }
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()){
            Query<KruizeRecommendationEntry> query = session.createQuery(SELECT_FROM_RECOMMENDATIONS_BY_CLUSTER_NAME, KruizeRecommendationEntry.class)
                    .setParameter("clusterName", clusterName)
                    .setParameter("experimentList", experimentNames)
                    .setParameter("limit", experimentNames.size());
            ScrollableResults<KruizeRecommendationEntry> results = query.scroll();

            while (results.next()) {
                KruizeRecommendationEntry recommendationEntry = results.get();
                Hibernate.initialize(recommendationEntry.getExtended_data());

                recommendationEntries.add(recommendationEntry);
            }
        } catch (Exception e) {
            LOGGER.error("Not able to load recommendations due to {}", e.getMessage());
            throw new Exception("Error while loading existing recommendations from database due to : " + e.getMessage());
        }
        return recommendationEntries;
    }

    @Override
    public List<KruizeExperimentEntry> loadExperimentsByNamespaceName(String namespaceName) throws Exception {
        List<KruizeExperimentEntry> entries;
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            EntityManager entityManager = session.getEntityManagerFactory().createEntityManager();
            String namespaceFilter = "[{\"namespace\": \"" + namespaceName + "\"}]";    //TODO: need to think of a better way to set up this query

            jakarta.persistence.Query query = entityManager.createNativeQuery(SELECT_FROM_EXPERIMENTS_BY_NAMESPACE_NAME, KruizeExperimentEntry.class)
                    .setParameter("namespaceFilter", namespaceFilter);
            entries = query.getResultList();
        } catch (Exception e) {
            LOGGER.error("Not able to load experiment due to {}", e.getMessage());
            throw new Exception("Error while loading existing experiment from database due to : " + e.getMessage());
        }
        return entries;
    }

    @Override
    public HashMap<String, List<String>> loadAllClusterNamespaceAssociation() throws Exception {
        HashMap<String, List<String>> entries = new HashMap<>();
        try (Session session = KruizeHibernateUtil.getSessionFactory().openSession()) {
            EntityManager entityManager = session.getEntityManagerFactory().createEntityManager();

            Query query = (Query) entityManager.createNativeQuery(SELECT_CLUSTERS_AND_NAMESPACE_FROM_RECOMMENDATIONS);
            List<Object[]> results = query.getResultList();

            for (Object[] result : results) {
                String clusterName = (String) result[0];
                List<String> namespaces = (List<String>) result[1];
                entries.put(clusterName, namespaces);
            }
        } catch (Exception e) {
            LOGGER.error("Not able to load experiment due to {}", e.getMessage());
            throw new Exception("Error while loading existing experiment from database due to : " + e.getMessage());
        }
        return entries;
    }
}

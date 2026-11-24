/**
 * Official warnings from MeteoAlarm (#136): the Italian feed is polled every few minutes and its active warnings
 * are kept in memory, keyed by the warning's identifier and its region ({@code EMMA_ID}), with the Italian text
 * read once from each warning's CAP document. Nothing is stored in the database. The warnings are shown as they
 * are, never mixed with the computed alerts: the issuing service, the time of issue, the link to meteoalarm.org
 * and the disclaimer travel with them.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Official Warnings",
        allowedDependencies = { "garden", "geocoding", "resilience" }
)
package app.rootology.officialwarnings;

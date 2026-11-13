/**
 * Usage of the paid services per person (#19): every pipeline run someone starts is recorded with its
 * Claude calls, tokens, estimated cost and Jev calls; questions are limited per person and per day (Italian
 * time), with a configured default and personal limits set by an admin. The check comes before any paid call.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Usage")
package app.rootology.usage;

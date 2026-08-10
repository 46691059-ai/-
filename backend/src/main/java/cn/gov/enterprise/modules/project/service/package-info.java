/**
 * Compatibility services for the existing Project API.
 *
 * <p>{@code ProjectLifecycleService} and its implementation remain the production path during the
 * incremental aggregate migration. Create and detail have moved to the application layer. Stage
 * read, task read/create/update, and member read/create/delete have also moved; page, project
 * update/delete, stage update, task delete, and member update remain here. These services must not
 * be removed until every use case has moved and the API, security and data-scope regression suites
 * pass.</p>
 */
package cn.gov.enterprise.modules.project.service;

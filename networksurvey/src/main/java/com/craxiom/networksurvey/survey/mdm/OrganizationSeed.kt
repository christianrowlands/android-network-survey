package com.craxiom.networksurvey.survey.mdm

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType

/**
 * The organization default setup the deprecated MDM keys describe. It seeds the user's setup on a
 * managed phone; it is not a lock.
 *
 * An empty [recordTypes] is ambiguous on its own: the keys may name no record types at all (the
 * user's own types are kept), or they may name none on purpose. [namesRecordTypes] tells the two
 * apart.
 *
 * @property recordTypes      The record types the keys turn on.
 * @property destinations     The destinations the keys turn on.
 * @property namesRecordTypes True if the keys decide the record types, even as none. An MDM broker
 *                            with every stream flag off is the case for none: earlier versions
 *                            streamed only device status to it, so the seed keeps no record types
 *                            instead of falling back to the user's.
 */
data class OrganizationSeed(
    val recordTypes: Set<RecordType>,
    val destinations: Set<Destination>,
    val namesRecordTypes: Boolean = recordTypes.isNotEmpty(),
)

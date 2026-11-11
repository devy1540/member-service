package com.api.member.infrastructure.client

import com.api.member.application.query.*
import com.api.member.domain.GroupPricing
import com.api.member.domain.MembershipPermission
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class GroupsSummaryHttpResponse(val groups: List<GroupSummaryHttpResponse?>?)
internal data class GroupSummaryHttpResponse(
    val groupId: String, val groupName: String, val totalMemberCount: Long?,
    val members: List<GroupSummaryMemberHttpResponse?>?,
)
internal data class GroupSummaryMemberHttpResponse(val memberId: String, val permission: MembershipPermission)
internal data class GroupDetailHttpResponse(
    val memberId: String, val groupId: String, val groupName: String, val registeredDate: String,
    val pricing: GroupPricing, val permission: MembershipPermission,
)

@Component
class GroupMembershipHttpAdapter(
    @Qualifier("groupServiceRestClient") private val client: RestClient,
    private val mapper: JsonMapper,
) : GroupMembershipReadPort, GroupDetailReadPort {
    private val log = LoggerFactory.getLogger(javaClass)
    private val validId = Regex("[A-Za-z0-9]{1,32}")
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    override fun findByMemberIds(memberIds: List<String>): Map<String, GroupMembershipReadModel> {
        if (memberIds.isEmpty()) return emptyMap()
        val started = System.nanoTime()
        try {
            val response = try {
                client.get().uri { uri ->
                    uri.path("/api/groups/summary").queryParam("memberIds", memberIds.joinToString(",")).build()
                }.retrieve().body(GroupsSummaryHttpResponse::class.java) ?: invalidResponse()
            } catch (exception: RestClientException) {
                throw classify(exception)
            }
            val groups = response.groups ?: invalidResponse()
            if (groups.any { it == null }) invalidResponse()
            val memberships = linkedMapOf<String, GroupMembershipReadModel>()
            val groupIds = mutableSetOf<String>()
            val requestedIds = memberIds.toSet()
            val receivedIds = mutableSetOf<String>()
            groups.filterNotNull().forEach { group ->
                if (!validId.matches(group.groupId) || !groupIds.add(group.groupId)) invalidResponse()
                val members = group.members ?: invalidResponse()
                if (members.any { it == null } || group.totalMemberCount != members.size.toLong()) invalidResponse()
                val entries = members.filterNotNull()
                if (entries.none { it.memberId in requestedIds }) invalidResponse()
                entries.forEach { member ->
                    if (!validId.matches(member.memberId) || !receivedIds.add(member.memberId)) invalidResponse()
                    // The summary contains the whole group; only this member page is enriched.
                    if (member.memberId in requestedIds) {
                        memberships[member.memberId] = GroupMembershipReadModel(member.memberId, group.groupName, member.permission)
                    }
                }
            }
            val durationMs = elapsed(started)
            log.atInfo().addKeyValue("eventType", "group.lookup.completed")
                .addKeyValue("cqrs", "query").addKeyValue("upstreamService", "group-service")
                .addKeyValue("requestedCount", memberIds.size).addKeyValue("returnedCount", memberships.size)
                .addKeyValue("receivedMemberCount", receivedIds.size)
                .addKeyValue("durationMs", durationMs)
                .log("Group lookup completed (requested={}, returned={}, durationMs={})", memberIds.size, memberships.size, durationMs)
            return memberships
        } catch (exception: GroupMembershipLookupException) {
            logFailure("group.lookup.failed", started, memberIds.size, exception)
            throw exception
        }
    }

    override fun findByMemberId(memberId: String): GroupDetailReadModel? {
        val started = System.nanoTime()
        try {
            val body = try {
                client.get().uri { it.path("/api/group").queryParam("memberId", memberId).build() }
                    .retrieve().body(JsonNode::class.java) ?: invalidResponse()
            } catch (exception: RestClientException) {
                throw classify(exception)
            }
            val result = if (body.isNull) null else {
                if (!body.isObject) invalidResponse()
                val value = try {
                    mapper.treeToValue(body, GroupDetailHttpResponse::class.java)
                } catch (exception: JacksonException) {
                    throw GroupMembershipLookupException(GroupLookupFailureReason.INVALID_RESPONSE, cause = exception)
                }
                if (value.memberId != memberId || !validId.matches(value.groupId)) invalidResponse()
                val date = try {
                    LocalDateTime.parse(value.registeredDate, dateFormat).atOffset(ZoneOffset.UTC)
                } catch (exception: DateTimeException) {
                    throw GroupMembershipLookupException(GroupLookupFailureReason.INVALID_RESPONSE, cause = exception)
                }
                GroupDetailReadModel(value.groupId, value.groupName, date, value.pricing, value.permission)
            }
            log.atInfo().addKeyValue("eventType", "group.detail.lookup.completed")
                .addKeyValue("cqrs", "query").addKeyValue("upstreamService", "group-service")
                .addKeyValue("found", result != null).addKeyValue("durationMs", elapsed(started))
                .log("Group detail lookup completed (found={})", result != null)
            return result
        } catch (exception: GroupMembershipLookupException) {
            logFailure("group.detail.lookup.failed", started, 1, exception)
            throw exception
        }
    }

    private fun invalidResponse(): Nothing = throw GroupMembershipLookupException(GroupLookupFailureReason.INVALID_RESPONSE)
    private fun elapsed(started: Long) = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

    private fun logFailure(eventType: String, started: Long, requestedCount: Int, exception: GroupMembershipLookupException) {
        log.atWarn().addKeyValue("eventType", eventType)
            .addKeyValue("cqrs", "query").addKeyValue("upstreamService", "group-service")
            .addKeyValue("requestedCount", requestedCount)
            .addKeyValue("failureReason", exception.reason.name).addKeyValue("upstreamStatus", exception.upstreamStatus)
            .addKeyValue("errorType", exception.cause?.javaClass?.simpleName ?: exception.javaClass.simpleName)
            .addKeyValue("durationMs", elapsed(started))
            .log("Group lookup failed (reason={}, status={})", exception.reason.name, exception.upstreamStatus)
    }

    private fun classify(exception: RestClientException): GroupMembershipLookupException {
        val reason = when (exception) {
            is RestClientResponseException -> GroupLookupFailureReason.HTTP_ERROR
            is ResourceAccessException -> {
                val timeout = generateSequence<Throwable>(exception) { it.cause }
                    .any { it is HttpTimeoutException || it is SocketTimeoutException }
                if (timeout) GroupLookupFailureReason.TIMEOUT else GroupLookupFailureReason.CONNECTION_FAILURE
            }
            else -> GroupLookupFailureReason.INVALID_RESPONSE
        }
        return GroupMembershipLookupException(reason, (exception as? RestClientResponseException)?.statusCode?.value(), exception)
    }
}

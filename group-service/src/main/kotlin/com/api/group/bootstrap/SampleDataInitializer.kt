package com.api.group.bootstrap

import com.api.group.application.command.InitialGroup
import com.api.group.application.command.InitialMembership
import com.api.group.application.command.InitializeGroupsCommand
import com.api.group.application.command.InitializeGroupsCommandHandler
import com.api.group.domain.Permission
import com.api.group.domain.Pricing
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.sample-data", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@Order(1)
class SampleDataInitializer(private val handler: InitializeGroupsCommandHandler) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        handler.handle(InitializeGroupsCommand(listOf(
            InitialGroup("Free 샘플 그룹", Pricing.Free, listOf(
                InitialMembership("ax1qwsd12s", Permission.Admin),
                InitialMembership("bs8as1vfws", Permission.Editor),
            ), id = "arutjf9348"),
            InitialGroup("Advanced 샘플 그룹", Pricing.Advanced, listOf(InitialMembership("ca12fdfdv1", Permission.Viewer)), id = "bsvkg0259a"),
            InitialGroup("Premium 샘플 그룹", Pricing.Premium, listOf(InitialMembership("dasdxcv22d", Permission.Admin)), id = "ctwlh136ab"),
        )))
    }
}

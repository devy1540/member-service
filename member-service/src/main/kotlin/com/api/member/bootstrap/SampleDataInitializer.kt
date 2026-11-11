package com.api.member.bootstrap

import com.api.member.application.command.InitialMember
import com.api.member.application.command.InitializeMembersCommand
import com.api.member.application.command.InitializeMembersCommandHandler
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.sample-data", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@Order(1)
class SampleDataInitializer(private val handler: InitializeMembersCommandHandler) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        handler.handle(InitializeMembersCommand(listOf(
            InitialMember("ax1qwsd12s", "김멤버", "member1@example.test", "서울특별시"),
            InitialMember("bs8as1vfws", "이멤버", "member2@example.test", "부산광역시"),
            InitialMember("ca12fdfdv1", "박멤버", "member3@example.test", "대전광역시"),
            InitialMember("dasdxcv22d", "최멤버", "member4@example.test", "인천광역시"),
            InitialMember("ev4nmn33ea", "정멤버", "member5@example.test", "광주광역시"),
            InitialMember("fw5pqr44fb", "한멤버", "member6@example.test", "울산광역시"),
            InitialMember("gx6stu55gc", "오멤버", "member7@example.test", "경기도 수원시"),
        )))
    }
}

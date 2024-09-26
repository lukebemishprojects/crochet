package dev.lukebemish.crochet.internal;

public final class Log4jSetup {
    private Log4jSetup() {}

    public static final String FABRIC_CONFIG = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Configuration status="WARN">
            <Appenders>
                <Console name="SysOut" target="SYSTEM_OUT">
                    <PatternLayout>
                        <LoggerNamePatternSelector defaultPattern="%style{[%d{HH:mm:ss}]}{blue} %highlight{[%t/%level]}{FATAL=red, ERROR=red, WARN=yellow, INFO=green, DEBUG=green, TRACE=blue} %style{(%logger{1})}{cyan} %highlight{%msg%n}{FATAL=red, ERROR=red, WARN=normal, INFO=normal, DEBUG=normal, TRACE=normal}" disableAnsi="${sys:fabric.log.disableAnsi:-true}">
                            <PatternMatch key="net.minecraft.,com.mojang." pattern="%style{[%d{HH:mm:ss}]}{blue} %highlight{[%t/%level]}{FATAL=red, ERROR=red, WARN=yellow, INFO=green, DEBUG=green, TRACE=blue} %style{(Minecraft)}{cyan} %highlight{%msg{nolookups}%n}{FATAL=red, ERROR=red, WARN=normal, INFO=normal, DEBUG=normal, TRACE=normal}"/>
                        </LoggerNamePatternSelector>
                    </PatternLayout>
                    <Filters>
                        <RegexFilter regex="^Failed to verify authentication$" onMatch="DENY" onMismatch="NEUTRAL"/>
                        <RegexFilter regex="^Failed to fetch user properties$" onMatch="DENY" onMismatch="NEUTRAL"/>
                        <RegexFilter regex="^Couldn't connect to realms$" onMatch="DENY" onMismatch="NEUTRAL"/>
                    </Filters>
                </Console>
                <Queue name="ServerGuiConsole">
                    <PatternLayout>
                        <LoggerNamePatternSelector defaultPattern="[%d{HH:mm:ss} %level] (%logger{1}) %msg{nolookups}%n">
                            <PatternMatch key="net.minecraft.,com.mojang." pattern="[%d{HH:mm:ss} %level] %msg{nolookups}%n"/>
                        </LoggerNamePatternSelector>
                    </PatternLayout>
                </Queue>
                <RollingRandomAccessFile name="LatestFile" fileName="logs/latest.log" filePattern="logs/%d{yyyy-MM-dd}-%i.log.gz">
                    <PatternLayout>
                        <LoggerNamePatternSelector defaultPattern="[%d{HH:mm:ss} %level] (%logger{1}) %msg{nolookups}%n">
                            <PatternMatch key="net.minecraft.,com.mojang." pattern="[%d{HH:mm:ss} %level] %msg{nolookups}%n"/>
                        </LoggerNamePatternSelector>
                    </PatternLayout>
                    <Policies>
                        <TimeBasedTriggeringPolicy/>
                        <OnStartupTriggeringPolicy/>
                    </Policies>
                </RollingRandomAccessFile>
                <RollingRandomAccessFile name="DebugFile" fileName="logs/debug.log" filePattern="logs/debug-%i.log.gz">
                    <PatternLayout>
                        <LoggerNamePatternSelector defaultPattern="[%d{HH:mm:ss} %level] (%logger{1}) %msg{nolookups}%n">
                            <PatternMatch key="net.minecraft.,com.mojang." pattern="[%d{HH:mm:ss} %level] %msg{nolookups}%n"/>
                        </LoggerNamePatternSelector>
                    </PatternLayout>
                    <DefaultRolloverStrategy max="3" fileIndex="min"/>
                    <Policies>
                        <SizeBasedTriggeringPolicy size="200MB"/>
                        <OnStartupTriggeringPolicy />
                    </Policies>
                </RollingRandomAccessFile>
            </Appenders>
            <Loggers>
                <Root level="all">
                    <AppenderRef ref="DebugFile" level="${sys:fabric.log.debug.level:-debug}"/>
                    <AppenderRef ref="SysOut" level="${sys:fabric.log.level:-info}"/>
                    <AppenderRef ref="LatestFile" level="${sys:fabric.log.level:-info}"/>
                    <AppenderRef ref="ServerGuiConsole" level="${sys:fabric.log.level:-info}"/>
                </Root>
            </Loggers>
        </Configuration>
        """;
}

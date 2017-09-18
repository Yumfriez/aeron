/*
 * Copyright 2014-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.agent;

import io.aeron.driver.EventLog;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.utility.JavaModule;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.asm.Advice.to;
import static net.bytebuddy.matcher.ElementMatchers.*;

@SuppressWarnings("unused")
public class EventLogAgent
{
    private static final long SLEEP_PERIOD_MS = 1L;
    private static final EventLogReaderAgent EVENT_LOG_READER_AGENT = new EventLogReaderAgent();

    private static AgentRunner readerAgentRunner;
    private static Instrumentation instrumentation;
    private static volatile ClassFileTransformer logTransformer;

    private static final AgentBuilder.Listener LISTENER = new AgentBuilder.Listener()
    {
        public void onDiscovery(
            final String typeName,
            final ClassLoader classLoader,
            final JavaModule module,
            final boolean loaded)
        {
        }

        public void onTransformation(
            final TypeDescription typeDescription,
            final ClassLoader classLoader,
            final JavaModule module,
            final boolean loaded,
            final DynamicType dynamicType)
        {
            System.out.println("TRANSFORM " + typeDescription.getName());
        }

        public void onIgnored(
            final TypeDescription typeDescription,
            final ClassLoader classLoader,
            final JavaModule module,
            final boolean loaded)
        {
        }

        public void onError(
            final String typeName,
            final ClassLoader classLoader,
            final JavaModule module,
            final boolean loaded,
            final Throwable throwable)
        {
            System.out.println("ERROR " + typeName);
            throwable.printStackTrace(System.err);
        }

        public void onComplete(
            final String typeName,
            final ClassLoader classLoader,
            final JavaModule module,
            final boolean loaded)
        {
        }
    };

    private static void agent(final boolean shouldRedefine, final Instrumentation instrumentation)
    {
        if (EventConfiguration.ENABLED_EVENT_CODES == 0)
        {
            return;
        }

        /*
         * Intercept based on enabled events:
         *  SenderProxy
         *  ReceiverProxy
         *  ClientProxy
         *  DriverConductor (onClientCommand)
         *  SendChannelEndpoint
         *  ReceiveChannelEndpoint
         */

        EventLogAgent.instrumentation = instrumentation;

        readerAgentRunner = new AgentRunner(
            new SleepingMillisIdleStrategy(SLEEP_PERIOD_MS),
            Throwable::printStackTrace,
            null,
            EVENT_LOG_READER_AGENT);

        logTransformer = new AgentBuilder.Default(new ByteBuddy().with(TypeValidation.DISABLED))
            .with(LISTENER)
            .disableClassFormatChanges()
            .with(shouldRedefine ?
                AgentBuilder.RedefinitionStrategy.RETRANSFORMATION :
                AgentBuilder.RedefinitionStrategy.DISABLED)
            .type(nameEndsWith("DriverConductor"))
            .transform((builder, typeDescription, classLoader, javaModule) ->
                builder
                    .visit(to(CleanupInterceptor.DriverConductorInterceptor.CleanupImage.class)
                        .on(named("cleanupImage")))
                    .visit(to(CleanupInterceptor.DriverConductorInterceptor.CleanupPublication.class)
                        .on(named("cleanupPublication")))
                    .visit(to(CleanupInterceptor.DriverConductorInterceptor.CleanupSubscriptionLink.class)
                        .on(named("cleanupSubscriptionLink"))))
            .type(nameEndsWith("ClientRequestAdapter"))
            .transform((builder, typeDescription, classLoader, javaModule) ->
                builder
                    .visit(to(CmdInterceptor.class).on(named("onMessage"))))
            .type(nameEndsWith("ClientProxy"))
            .transform((builder, typeDescription, classLoader, javaModule) ->
                builder.visit(to(CmdInterceptor.class).on(named("transmit"))))
            .type(nameEndsWith("SenderProxy"))
            .transform((builder, typeDescription, classLoader, javaModule) ->
                builder
                    .visit(to(ChannelEndpointInterceptor.SenderProxyInterceptor.RegisterSendChannelEndpoint.class)
                        .on(named("registerSendChannelEndpoint")))
                    .visit(to(ChannelEndpointInterceptor.SenderProxyInterceptor.CloseSendChannelEndpoint.class)
                        .on(named("closeSendChannelEndpoint"))))
            .type(nameEndsWith("ReceiverProxy"))
            .transform((builder, typeDescription, classLoader, javaModule) ->
                builder
                    .visit(to(ChannelEndpointInterceptor.ReceiverProxyInterceptor.RegisterReceiveChannelEndpoint.class)
                        .on(named("registerReceiveChannelEndpoint")))
                    .visit(to(ChannelEndpointInterceptor.ReceiverProxyInterceptor.CloseReceiveChannelEndpoint.class)
                        .on(named("closeReceiveChannelEndpoint"))))
            .type(inheritsAnnotation(EventLog.class))
            .transform((builder, typeDescription, classLoader, javaModule) ->
                builder
                    .visit(to(ChannelEndpointInterceptor.SendChannelEndpointInterceptor.Presend.class)
                        .on(named("presend")))
                    .visit(to(ChannelEndpointInterceptor.ReceiveChannelEndpointInterceptor.SendTo.class)
                        .on(named("sendTo")))
                    .visit(to(ChannelEndpointInterceptor.SendChannelEndpointInterceptor.OnStatusMessage.class)
                        .on(named("onStatusMessage")))
                    .visit(to(ChannelEndpointInterceptor.SendChannelEndpointInterceptor.OnNakMessage.class)
                        .on(named("onNakMessage")))
                    .visit(to(ChannelEndpointInterceptor.SendChannelEndpointInterceptor.OnRttMeasurement.class)
                        .on(named("onRttMeasurement")))
                    .visit(to(ChannelEndpointInterceptor.ReceiveChannelEndpointInterceptor.OnDataPacket.class)
                        .on(named("onDataPacket")))
                    .visit(to(ChannelEndpointInterceptor.ReceiveChannelEndpointInterceptor.OnSetupMessage.class)
                        .on(named("onSetupMessage")))
                    .visit(to(ChannelEndpointInterceptor.ReceiveChannelEndpointInterceptor.OnRttMeasurement.class)
                        .on(named("onRttMeasurement"))))
            .installOn(instrumentation);

        final Thread thread = new Thread(readerAgentRunner);
        thread.setName("event log reader");
        thread.setDaemon(true);
        thread.start();
    }

    public static void premain(final String agentArgs, final Instrumentation instrumentation)
    {
        agent(false, instrumentation);
    }

    public static void agentmain(final String agentArgs, final Instrumentation instrumentation)
    {
        agent(true, instrumentation);
    }

    public static void removeTransformer()
    {
        if (logTransformer != null)
        {
            readerAgentRunner.close();
            instrumentation.removeTransformer(logTransformer);
            instrumentation.removeTransformer(new AgentBuilder.Default()
                .type(nameEndsWith("DriverConductor")
                    .or(nameEndsWith("ClientProxy"))
                    .or(nameEndsWith("ClientRequestAdapter"))
                    .or(nameEndsWith("SenderProxy"))
                    .or(nameEndsWith("ReceiverProxy"))
                    .or(inheritsAnnotation(EventLog.class)))
                .transform(AgentBuilder.Transformer.NoOp.INSTANCE)
                .installOn(instrumentation));

            readerAgentRunner = null;
            instrumentation = null;
            logTransformer = null;
        }
    }
}

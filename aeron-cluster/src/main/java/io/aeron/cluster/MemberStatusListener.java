/*
 * Copyright 2014-2018 Real Logic Ltd.
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
package io.aeron.cluster;

import io.aeron.cluster.codecs.RecordingLogDecoder;
import io.aeron.cluster.codecs.RecoveryPlanDecoder;

interface MemberStatusListener
{
    void onCanvassPosition(long logPosition, long leadershipTermId, int followerMemberId);

    void onRequestVote(long logPosition, long candidateTermId, int candidateId);

    void onVote(long candidateTermId, int candidateMemberId, int followerMemberId, boolean vote);

    void onNewLeadershipTerm(long logPosition, long leadershipTermId, int leaderMemberId, int logSessionId);

    void onAppendedPosition(long logPosition, long leadershipTermId, int followerMemberId);

    void onCommitPosition(long logPosition, long leadershipTermId, int leaderMemberId);

    void onRecoveryPlanQuery(long correlationId, int requestMemberId, int leaderMemberId);

    void onRecoveryPlan(RecoveryPlanDecoder recoveryPlanDecoder);

    void onRecordingLogQuery(
        long correlationId,
        int requestMemberId,
        int leaderMemberId,
        long fromLeadershipTermId,
        int count,
        boolean includeSnapshots);

    void onRecordingLog(RecordingLogDecoder recordingLogDecoder);
}

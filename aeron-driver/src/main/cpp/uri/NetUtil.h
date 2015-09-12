//
// Created by Michael Barker on 01/09/15.
//

#ifndef INCLUDE_AERON_DRIVER_URI_NET_UTIL_
#define INCLUDE_AERON_DRIVER_URI_NET_UTIL_

#include <arpa/inet.h>
#include <cinttypes>

namespace aeron { namespace driver { namespace uri {

class NetUtil
{
public:
    static bool wildcardMatch(const struct in6_addr* data, const struct in6_addr* pattern, std::uint32_t prefixLength);
    static bool wildcardMatch(const struct in_addr* data, const struct in_addr* pattern, std::uint32_t prefixLength);
    static bool isEven(in_addr ipV4);
    static bool isEven(in6_addr const & ipV6);
};

}}}



#endif //AERON_NETUTIL_H

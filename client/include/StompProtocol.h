#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h" 
#include <string>
#include <vector>
#include <map>
// TODO: implement the STOMP protocol

struct GameState {
    std::string team_a;
    std::string team_b;
    std::map<std::string, std::string> general_stats;
    std::map<std::string, std::string> team_a_stats;
    std::map<std::string, std::string> team_b_stats;
    std::vector<Event> events; // stores the raw events
};
class StompProtocol
{
private:

    ConnectionHandler* handler;
    
    bool shouldTerminate;
    bool isLoggedIn;
    std::string username;

    int subscriptionIdCounter;
    int receiptIdCounter;

    std::map<std::string, int> topicToSubId;
    std::map<int, std::string> subIdToTopic;
    std::map<int, std::string> receiptForCommand;

    std::map<std::string, std::map<std::string, GameState>> gameStorage; 
public:
    StompProtocol(ConnectionHandler* handler);
    //destructor is default because we don't create new memory anywhere
    virtual ~StompProtocol() = default;
    void process(std::string message);//server
    bool processKeyboard(std::string commandLine);//user
    bool shouldStop() const;
    void setShouldTerminate(bool terminate);
};

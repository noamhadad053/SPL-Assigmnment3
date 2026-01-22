#include <stdlib.h>
#include <iostream>
#include <thread>
#include <vector>
#include <string> 
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

using namespace std;

int main(int argc, char *argv[]) {
	// TODO: implement the STOMP client

	if (argc < 3) {
        cerr << "Usage: " << argv[0] << " host port" << endl << endl;
        return -1;
    }
	string host = argv[1];
    short port = atoi(argv[2]);

	ConnectionHandler connectionHandler(host, port);
    if (!connectionHandler.connect()) {
        cerr << "Cannot connect to " << host << ":" << port << endl;
        return 1;
    }

	StompProtocol protocol(&connectionHandler);

	thread socketThread([&connectionHandler, &protocol]() {
        while (!protocol.shouldStop()) {
            string answer;
            
            
            if (!connectionHandler.getFrameAscii(answer, '\0')) {
                cout << "Disconnected from server. Press enter to exit.\n" << endl;
                protocol.setShouldTerminate(true);//tell main thread to stop stop
                break;
            }

            //pass the frame
            protocol.process(answer);
        }
    });

	while (!protocol.shouldStop()) {
        const short bufsize = 1024;
        char buf[bufsize];
        
        // waits until you type enter
        cin.getline(buf, bufsize);
        string line(buf);
        
        // Pass the command to the protocol logic
        // (e.g., parsing "login...", "join...", "report...")
        protocol.processKeyboard(line);
    }

	if (socketThread.joinable()) {
        socketThread.join();
    }

	return 0;
}
open module sut.civilization {
    requires java.desktop;
    requires com.google.gson;

    exports sut.civilization.Model.Classes;

    exports sut.civilization.View.Graphical;

    exports sut.civilization.Model.ModulEnums;

    exports sut.civilization.Enums;

    exports sut.civilization;

    exports sut.civilization.Network.Messages;

    exports sut.civilization.Network.Server;

    exports sut.civilization.Network.Client;
}

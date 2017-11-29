pragma solidity ^0.4.11;

contract ICO_ABT {
    // 100M coins by default
    uint constant totalSupplyDefault = 100*1000*1000;
    uint public totalSupply;
    string public constant symbol = "ABT";
    string public constant name = "AssetBase Token";
    uint8 public constant decimals = 0;
    
    // Owner of this contract
    address public owner;
    modifier onlyOwner() {
        if (msg.sender != owner) {
            revert();
        }
        _;
    }
 
    // Balances for each account
    mapping(address => uint) balances;
 
    // Owner of account approves the transfer of an amount to another account
    mapping(address => mapping (address => uint)) allowed;

    // sale can be started manually
    bool public saleActive = false;

    // Sale stopped - after that buyers can trade tokens
    bool public saleStopped = false;

    // how many tokens issued in current lot
    uint public lotTokens = 0;
    // how many tokens left in current lot
    uint public lotTokensLeft = 0;
    // price for current/last lotTokens
    uint public lotTokensPerEther = 0;

    // each pre-sale address has tokens per ether and max amount to buy
    struct preSaleT {
        uint tknsPerEther;
        uint amount;
    }
    mapping (address => preSaleT) _preSales;

    // any addresscan be frozen to not trade
    mapping (address => bool) public frozenAccount;

    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed from , address indexed to , uint256 value);
    event FrozenFunds(address target, bool frozen);

    // if supply provided is 0, then default assigned
    function ICO_ABT(uint supply) {
        if (supply > 0) {
            totalSupply = supply;
        } else {
            totalSupply = totalSupplyDefault;
        }
        owner = msg.sender;
        balances[this] = totalSupply;
    }
 
    function balanceOf(address addr) constant returns (uint) {
        return balances[addr];
    }
 
    // allow transfer only after stop sale, owner can transfer ane time
    function transfer(address to, uint amount) returns (bool) {
        if ( (saleStopped || msg.sender == owner)
            && balances[msg.sender] >= amount
            && amount > 0
            && balances[to] + amount > balances[to]
            && !frozenAccount[msg.sender]
            ) {
            balances[msg.sender] -= amount;
            balances[to] += amount;
            Transfer(msg.sender, to, amount);
            return true;
        } else {
            return false;
        }
    }
 
    // allow transfer only after stop sale, owner can transfer ane time
    function transferFrom(address from, address to, uint amount) returns (bool) {
        if ( (saleStopped || msg.sender == owner)
            && balances[from] >= amount
            && allowed[from][msg.sender] >= amount
            && amount > 0
            && balances[to] + amount > balances[to]
            && !frozenAccount[msg.sender]
            ) {
            balances[from] -= amount;
            allowed[from][msg.sender] -= amount;
            balances[to] += amount;
            Transfer(from, to, amount);
            return true;
        } else {
            return false;
        }
    }
 
    function approve(address spender, uint amount) returns (bool) {
        allowed[msg.sender][spender] = amount;
        Approval(msg.sender, spender, amount);
        return true;
    }
 
    function allowance(address addr, address spender) constant returns (uint) {
        return allowed[addr][spender];
    }

    // return not more than available amount
    function getAvailableTokens(uint req) internal returns (uint avail) {
        uint exists = balances[this];
        if (avail > exists) {
            avail = exists;
        } else {
            avail = req;
        }
    }

    // issue presale to investor spender
    function preSale(address spender, uint tknsPerEther, uint amount) onlyOwner {
        require(tknsPerEther != 0 && amount != 0);
        _preSales[spender] = preSaleT({tknsPerEther: tknsPerEther, amount: amount});
    }

    // returns information about presale:
    // price in integral coins, presale amount and already sold for address
    function preSaleAllowance(address spender) constant onlyOwner returns (uint tknsPerEther, uint amount, uint sold) {
        var p = _preSales[spender];
        tknsPerEther = p.tknsPerEther;
        amount = p.amount;
        sold = balances[spender];
    }

    // start new lot
    // public ICO become active after first lot started
    // tknsPerEther - tokens per ether for this slot
    // amount - maximum tokens amount for this lot
    function startLot(uint tknsPerEther, uint amount) onlyOwner {
        saleActive = true;
        lotTokensPerEther = tknsPerEther;
        lotTokens = getAvailableTokens(amount);
        lotTokensLeft = lotTokens;
    }

    // stop(interrupt) current lot
    function stopLot() onlyOwner {
        lotTokensLeft = 0;
        lotTokensPerEther = 0;
    }

    // stop public ICO sale
    // after public ICO stopped, investors can trade coins
    function stopSale() onlyOwner {
        saleActive = false;
        saleStopped = true;
    }

    // freeze account to not trade
    function freezeAccount(address target) onlyOwner {
        frozenAccount[target] = true;
        FrozenFunds(target, true);
    }

    // unfreeze account to trade again
    function unfreezeAccount(address target) onlyOwner {
        frozenAccount[target] = false;
        FrozenFunds(target, false);
    }

    // withdraw all funds to owner address
    function withdraw() public onlyOwner {
        owner.transfer(this.balance);
    }

    // default payable function
    function () payable {
        uint tokens = 0;
        uint tknsPerEther = 0;
        if (!saleActive && !saleStopped) { // pre-sale section
            var vip = _preSales[msg.sender];
            if (vip.amount != 0) {
                tknsPerEther = vip.tknsPerEther;
                var bal = balances[msg.sender];
                if (vip.amount > bal) {
                    var max_amount = vip.amount - bal;
                    tokens = getAvailableTokens(tknsPerEther * msg.value / 1 ether);
                    if (tokens > max_amount) {
                        tokens = max_amount;
                    }
                }
            }
        } else if (lotTokensLeft != 0) { // lot sale condition
            tknsPerEther = lotTokensPerEther;
            tokens = getAvailableTokens(tknsPerEther * msg.value / 1 ether);
            if (tokens > lotTokensLeft) {
                tokens = lotTokensLeft;
            }
            lotTokensLeft -= tokens;
        }
        uint value_wei = 0;
        if (tknsPerEther > 0) {
            value_wei = tokens * 1 ether / tknsPerEther;
        }
        if (msg.value > value_wei) {
            msg.sender.transfer(msg.value - value_wei);
        }
        if (tokens > 0) {
            balances[msg.sender] += tokens;
            balances[this] -= tokens;
            Transfer(this, msg.sender, tokens);
        }
    }
}